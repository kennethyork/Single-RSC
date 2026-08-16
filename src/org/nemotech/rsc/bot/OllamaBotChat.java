package org.nemotech.rsc.bot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.lang.reflect.Type;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.nemotech.rsc.Constants;

/** Non-blocking, Ollama-only conversation engine for autonomous world bots. */
public final class OllamaBotChat {

    private static final String HISTORY_FILE = Constants.CACHE_DIRECTORY + "ollama-conversations.json";
    private static final String MODEL_FILE = Constants.CACHE_DIRECTORY + "ollama-model.properties";
    private static final int MAX_SAVED_CONVERSATIONS = 500;
    private static final int MAX_GLOBAL_QUEUE = 32;
    // A second local game may be using the same Ollama model. Serializing RSC's
    // requests prevents parallel contexts from exhausting RAM/VRAM and freezing
    // the game while still letting Ollama share the loaded model between games.
    private static final int MAX_CONCURRENT_REQUESTS = 1;
    private static final int OLLAMA_CONTEXT_TOKENS = 2048;
    private static final long HISTORY_SAVE_DEBOUNCE_MILLIS = 2000L;
    private static final Pattern MODEL_NAME = Pattern.compile(
            "[A-Za-z0-9][A-Za-z0-9._/-]{0,98}(?::[A-Za-z0-9][A-Za-z0-9._-]{0,98})?");
    private static final String[] SUGGESTED_MODELS = {
            "qwen3.5:4b", "llama3.2:3b", "gemma3:4b", "phi4-mini"
    };

    private static final OllamaBotChat INSTANCE = new OllamaBotChat();

    private final Gson gson = new Gson();
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();
    private final Map<String, Deque<Turn>> histories = new ConcurrentHashMap<>();
    private final Map<String, ConcurrentLinkedQueue<PendingReply>> queues = new ConcurrentHashMap<>();
    private final java.util.Set<String> activeConversations = ConcurrentHashMap.newKeySet();
    private final java.util.Set<String> waitingConversations = ConcurrentHashMap.newKeySet();
    private final Semaphore requestSlots = new Semaphore(MAX_CONCURRENT_REQUESTS);
    private final ScheduledExecutorService maintenanceExecutor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "ollama-bot-maintenance");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean historySaveScheduled = new AtomicBoolean(false);
    private final AtomicBoolean historyDirty = new AtomicBoolean(false);
    private final AtomicLong unavailableUntil = new AtomicLong();
    private final AtomicLong lastFailureLog = new AtomicLong();
    private final AtomicLong lastSuccessfulResponse = new AtomicLong();
    private volatile Settings settings = Settings.defaults();
    private volatile String modelOverride;
    private volatile boolean loaded;

    private OllamaBotChat() {}

    public static OllamaBotChat getInstance() {
        return INSTANCE;
    }

    public synchronized void configure(Settings updated) {
        settings = updated == null ? Settings.defaults() : updated;
        if (loaded) return;
        loaded = true;
        loadModelSelection();
        loadHistories();
    }

    public void reply(BotIdentity bot, String playerName, String channel, String input,
            Consumer<String> deliver, Runnable unavailable) {
        Settings current = settings;
        if (!current.enabled || System.currentTimeMillis() < unavailableUntil.get()) {
            if (unavailable != null) unavailable.run();
            return;
        }
        if (queuedCount() >= MAX_GLOBAL_QUEUE) {
            if (unavailable != null) unavailable.run();
            return;
        }
        String key = conversationKey(channel, playerName, bot.name);
        ConcurrentLinkedQueue<PendingReply> queue = queues.computeIfAbsent(key,
                ignored -> new ConcurrentLinkedQueue<>());
        if (queue.size() >= 10) return;
        queue.add(new PendingReply(bot, clean(playerName, 40), clean(channel, 20),
                clean(input, 240), deliver, unavailable));
        startNext(key);
    }

    public String activeModel() {
        return modelOverride == null || modelOverride.isBlank() ? settings.model : modelOverride;
    }

    public List<String> statusLines() {
        Settings current = settings;
        long now = System.currentTimeMillis();
        String state;
        if (!current.enabled) {
            state = "disabled; bots are silent";
        } else if (now < unavailableUntil.get()) {
            state = "temporarily unavailable; bots are silent and retrying";
        } else if (lastSuccessfulResponse.get() > 0) {
            state = "connected; last response " + Math.max(0, (now - lastSuccessfulResponse.get()) / 1000) + "s ago";
        } else {
            state = "enabled; waiting for the first response";
        }
        List<String> lines = new ArrayList<>();
        lines.add("Ollama bot chat: " + state);
        lines.add("Model: " + activeModel() + (modelOverride == null ? "" : " (in-game selection)"));
        lines.add("Endpoint: " + current.baseUrl + " | queued: " + queuedCount());
        lines.add("Memory: " + current.historyMessages + " messages, "
                + (current.persistHistory ? "saved locally" : "session only"));
        return lines;
    }

    public List<String> modelHelp() {
        List<String> lines = new ArrayList<>();
        lines.add("Current Ollama model: " + activeModel());
        lines.add("Use ::ollamamodel <model> or ::ollamamodel reset");
        lines.add("Suggested: " + String.join(", ", SUGGESTED_MODELS));
        lines.add("Install first in a terminal: ollama pull <model>");
        return lines;
    }

    public synchronized String selectModel(String requested) {
        String model = requested == null ? "" : requested.trim();
        if (!validModel(model)) {
            return "Invalid model name. Use letters, numbers, ., _, -, / and one optional :tag.";
        }
        if (!saveModelSelection(model)) return "The model selection could not be saved.";
        modelOverride = model;
        resetConnectionState();
        return "Bot model changed to " + model + " and saved for future sessions.";
    }

    public synchronized String resetModel() {
        if (!saveModelSelection("")) return "The model selection could not be reset.";
        modelOverride = null;
        resetConnectionState();
        return "Bot model reset to " + activeModel() + ".";
    }

    public int forget(String playerName) {
        String marker = ":" + clean(playerName, 40).toLowerCase() + ":";
        List<String> keys = new ArrayList<>();
        for (String key : histories.keySet()) if (key.contains(marker)) keys.add(key);
        for (String key : keys) histories.remove(key);
        requestPersistHistories();
        return keys.size();
    }

    public void probe(Consumer<String> deliver) {
        Settings current = settings;
        if (!current.enabled) {
            deliver.accept("Ollama is disabled; bots will remain silent.");
            return;
        }
        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(trimSlash(current.baseUrl) + "/api/tags"))
                    .timeout(Duration.ofSeconds(3)).GET().build();
        } catch (Exception e) {
            deliver.accept("Ollama endpoint is invalid; bots will remain silent.");
            return;
        }
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            String result;
            if (error == null && response != null && response.statusCode() == 200) {
                boolean installed = modelInstalled(response.body(), activeModel());
                result = installed ? "Ollama is online; " + activeModel() + " is installed."
                        : "Ollama is online, but " + activeModel() + " is missing. Run: ollama pull " + activeModel();
            } else {
                result = "Ollama is offline; bots will remain silent.";
            }
            deliver.accept(result);
        });
    }

    public boolean isLocalEndpoint() {
        try {
            String host = URI.create(settings.baseUrl).getHost();
            return host != null && (host.equalsIgnoreCase("localhost") || host.equals("::1")
                    || host.equals("127.0.0.1") || host.startsWith("127."));
        } catch (Exception e) {
            return false;
        }
    }

    private void startNext(String key) {
        if (!activeConversations.add(key)) return;
        if (!requestSlots.tryAcquire()) {
            activeConversations.remove(key);
            if (waitingConversations.add(key)) {
                maintenanceExecutor.schedule(() -> {
                    waitingConversations.remove(key);
                    startNext(key);
                }, 250L, TimeUnit.MILLISECONDS);
            }
            return;
        }
        PendingReply pending = queues.get(key) == null ? null : queues.get(key).poll();
        if (pending == null) {
            activeConversations.remove(key);
            queues.remove(key);
            requestSlots.release();
            return;
        }
        execute(key, pending);
    }

    private void execute(String key, PendingReply pending) {
        Settings current = settings;
        if (!current.enabled || System.currentTimeMillis() < unavailableUntil.get()) {
            finish(key, pending, null);
            return;
        }
        Deque<Turn> history = histories.computeIfAbsent(key, ignored -> new ArrayDeque<>());
        JsonArray messages = new JsonArray();
        messages.add(message("system", systemPrompt(pending)));
        synchronized (history) {
            history.addLast(new Turn("user", pending.input));
            trim(history);
            for (Turn turn : history) messages.add(message(turn.role, turn.content));
        }
        JsonObject body = new JsonObject();
        body.addProperty("model", activeModel());
        body.add("messages", messages);
        body.addProperty("stream", false);
        body.addProperty("think", false);
        JsonObject options = new JsonObject();
        options.addProperty("temperature", 0.8);
        options.addProperty("num_predict", 45);
        options.addProperty("num_ctx", OLLAMA_CONTEXT_TOKENS);
        body.add("options", options);
        body.addProperty("keep_alive", "5m");

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(trimSlash(current.baseUrl) + "/api/chat"))
                    .timeout(Duration.ofSeconds(Math.max(5, Math.min(120, current.timeoutSeconds))))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(body))).build();
        } catch (Exception e) {
            finish(key, pending, null);
            return;
        }
        client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).whenComplete((response, error) -> {
            String generated = error == null && response != null && response.statusCode() == 200
                    ? parseReply(response.body()) : null;
            if (generated == null) markUnavailable(error, response == null ? null : response.statusCode());
            else {
                lastSuccessfulResponse.set(System.currentTimeMillis());
                unavailableUntil.set(0);
            }
            finish(key, pending, generated);
        });
    }

    private void finish(String key, PendingReply pending, String generated) {
        Deque<Turn> history = histories.get(key);
        if (history != null) synchronized (history) {
            if (generated == null) {
                if (!history.isEmpty() && "user".equals(history.peekLast().role)) history.removeLast();
            } else {
                history.addLast(new Turn("assistant", generated));
                trim(history);
            }
        }
        try {
            if (generated != null) {
                requestPersistHistories();
                if (pending.deliver != null) pending.deliver.accept(generated);
            } else if (pending.unavailable != null) {
                pending.unavailable.run();
            }
        } finally {
            activeConversations.remove(key);
            requestSlots.release();
            startNext(key);
        }
    }

    private String systemPrompt(PendingReply pending) {
        BotIdentity bot = pending.bot;
        return "You are " + bot.name + ", a normal player inside RuneScape Classic. "
                + "You are combat level " + bot.combatLevel + " near " + bot.area + ". Your clan is " + bot.clan
                + " and its rival is " + bot.rivalClan + ". Your role is " + bot.title + ". "
                + "You are currently " + bot.activity + " and your goal is " + bot.goal + ". "
                + "Your interests are " + bot.interests + ". Keep this identity specific and consistent. "
                + "You are talking to " + pending.playerName + " in " + pending.channel + " chat. "
                + "Stay in character and never mention being a bot, AI, prompt, or language model. "
                + "Reply naturally in one short RuneScape Classic chat line, at most 100 characters. "
                + "No formatting, quotes, narration, links, slurs, sexual content, or real-world politics. "
                + "Remember the conversation. Do not invent game actions, items, trades, levels, or clan membership.";
    }

    private JsonObject message(String role, String content) {
        JsonObject message = new JsonObject();
        message.addProperty("role", role);
        message.addProperty("content", content);
        return message;
    }

    private String parseReply(String body) {
        try {
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();
            JsonObject message = root.getAsJsonObject("message");
            if (message == null || !message.has("content")) return null;
            String value = message.get("content").getAsString();
            value = value.replaceAll("<[^>]*>", " ").replaceAll("[\\r\\n\\t]+", " ")
                    .replaceAll("\\s+", " ").trim();
            while (!value.isEmpty() && "\"'`".indexOf(value.charAt(0)) >= 0) value = value.substring(1).trim();
            while (!value.isEmpty() && "\"'`".indexOf(value.charAt(value.length() - 1)) >= 0) {
                value = value.substring(0, value.length() - 1).trim();
            }
            if (value.length() > 100) value = value.substring(0, 100).trim();
            return value.isEmpty() ? null : value;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean modelInstalled(String body, String model) {
        try {
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();
            JsonArray models = root.getAsJsonArray("models");
            if (models == null) return false;
            for (JsonElement element : models) {
                JsonObject item = element.getAsJsonObject();
                if (item.has("name") && model.equals(item.get("name").getAsString())) return true;
                if (item.has("model") && model.equals(item.get("model").getAsString())) return true;
            }
        } catch (Exception ignored) {
        }
        return false;
    }

    private void trim(Deque<Turn> history) {
        int limit = Math.max(2, Math.min(20, settings.historyMessages));
        while (history.size() > limit) history.removeFirst();
    }

    private int queuedCount() {
        int count = 0;
        for (ConcurrentLinkedQueue<PendingReply> queue : queues.values()) count += queue.size();
        return count;
    }

    private String conversationKey(String channel, String player, String bot) {
        return clean(channel, 20).toLowerCase() + ":" + clean(player, 40).toLowerCase()
                + ":" + clean(bot, 40).toLowerCase();
    }

    private static String clean(String value, int limit) {
        if (value == null) return "";
        String clean = value.replaceAll("<[^>]*>", " ").replaceAll("[\\r\\n\\t]+", " ")
                .replaceAll("\\s+", " ").trim();
        return clean.length() > limit ? clean.substring(0, limit).trim() : clean;
    }

    private static String trimSlash(String value) {
        String result = value == null ? "" : value.trim();
        while (result.endsWith("/")) result = result.substring(0, result.length() - 1);
        return result;
    }

    private boolean validModel(String model) {
        return model.length() <= 199 && MODEL_NAME.matcher(model).matches()
                && !model.contains("..") && !model.contains("//");
    }

    private void resetConnectionState() {
        unavailableUntil.set(0);
        lastSuccessfulResponse.set(0);
    }

    private void markUnavailable(Throwable error, Integer status) {
        long now = System.currentTimeMillis();
        unavailableUntil.set(now + 30_000L);
        if (now - lastFailureLog.get() > 60_000L && lastFailureLog.getAndSet(now) < now - 60_000L) {
            System.out.println("[Ollama] Unavailable (" + (error == null ? "HTTP " + status
                    : error.getClass().getSimpleName()) + "); bots will remain silent");
        }
    }

    private synchronized void loadHistories() {
        if (!settings.persistHistory) return;
        File file = new File(HISTORY_FILE);
        if (!file.exists()) return;
        try (FileReader reader = new FileReader(file)) {
            Type type = new TypeToken<Map<String, List<Turn>>>() {}.getType();
            Map<String, List<Turn>> saved = gson.fromJson(reader, type);
            if (saved == null) return;
            int skipped = Math.max(0, saved.size() - MAX_SAVED_CONVERSATIONS);
            int index = 0;
            for (Map.Entry<String, List<Turn>> entry : saved.entrySet()) {
                if (index++ < skipped) continue;
                Deque<Turn> history = new ArrayDeque<>();
                List<Turn> turns = entry.getValue();
                int from = Math.max(0, turns.size() - Math.max(2, Math.min(20, settings.historyMessages)));
                for (int i = from; i < turns.size(); i++) history.addLast(turns.get(i));
                histories.put(entry.getKey(), history);
            }
            System.out.println("[Ollama] Restored " + histories.size() + " bot conversations");
        } catch (Exception e) {
            System.err.println("[Ollama] Could not load conversation memory: " + e.getMessage());
        }
    }

    private synchronized void persistHistories() {
        if (!settings.persistHistory) return;
        try {
            Map<String, List<Turn>> snapshot = new LinkedHashMap<>();
            int skip = Math.max(0, histories.size() - MAX_SAVED_CONVERSATIONS);
            int index = 0;
            for (Map.Entry<String, Deque<Turn>> entry : histories.entrySet()) {
                if (index++ < skip) continue;
                synchronized (entry.getValue()) {
                    snapshot.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
            }
            writeAtomic(HISTORY_FILE, gson.toJson(snapshot));
        } catch (Exception e) {
            System.err.println("[Ollama] Could not save conversation memory: " + e.getMessage());
        }
    }

    private void requestPersistHistories() {
        if (!settings.persistHistory) return;
        historyDirty.set(true);
        if (historySaveScheduled.compareAndSet(false, true)) {
            maintenanceExecutor.schedule(this::flushHistories,
                    HISTORY_SAVE_DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        }
    }

    private void flushHistories() {
        try {
            historyDirty.set(false);
            persistHistories();
        } finally {
            historySaveScheduled.set(false);
            if (historyDirty.get()) {
                requestPersistHistories();
            }
        }
    }

    private void loadModelSelection() {
        File file = new File(MODEL_FILE);
        if (!file.exists()) return;
        Properties properties = new Properties();
        try (FileInputStream input = new FileInputStream(file)) {
            properties.load(input);
            String saved = properties.getProperty("model", "").trim();
            if (validModel(saved)) modelOverride = saved;
        } catch (Exception e) {
            System.err.println("[Ollama] Could not restore selected model: " + e.getMessage());
        }
    }

    private boolean saveModelSelection(String model) {
        try {
            File file = new File(MODEL_FILE);
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            Properties properties = new Properties();
            properties.setProperty("model", model);
            try (FileOutputStream output = new FileOutputStream(file)) {
                properties.store(output, "Single-RSC selected Ollama model");
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void writeAtomic(String path, String content) throws Exception {
        File out = new File(path);
        File parent = out.getParentFile();
        if (parent != null) parent.mkdirs();
        File temporary = new File(parent == null ? new File(".") : parent, out.getName() + ".tmp");
        try (FileWriter writer = new FileWriter(temporary)) {
            writer.write(content);
        }
        try {
            Files.move(temporary.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temporary.toPath(), out.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static final class Turn {
        private String role;
        private String content;

        private Turn() {}

        private Turn(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }

    private static final class PendingReply {
        private final BotIdentity bot;
        private final String playerName;
        private final String channel;
        private final String input;
        private final Consumer<String> deliver;
        private final Runnable unavailable;

        private PendingReply(BotIdentity bot, String playerName, String channel, String input,
                Consumer<String> deliver, Runnable unavailable) {
            this.bot = bot;
            this.playerName = playerName;
            this.channel = channel;
            this.input = input;
            this.deliver = deliver;
            this.unavailable = unavailable;
        }
    }

    public static final class BotIdentity {
        public final String name;
        public final String title;
        public final String clan;
        public final String rivalClan;
        public final String area;
        public final String activity;
        public final String goal;
        public final String interests;
        public final int combatLevel;

        public BotIdentity(String name, String title, String clan, String rivalClan, String area,
                String activity, String goal, String interests, int combatLevel) {
            this.name = name;
            this.title = title;
            this.clan = clan;
            this.rivalClan = rivalClan;
            this.area = area;
            this.activity = activity;
            this.goal = goal;
            this.interests = interests;
            this.combatLevel = combatLevel;
        }
    }

    public static final class Settings {
        public final boolean enabled;
        public final String baseUrl;
        public final String model;
        public final int timeoutSeconds;
        public final int historyMessages;
        public final int publicCooldownSeconds;
        public final int clanCooldownSeconds;
        public final int ambientCooldownSeconds;
        public final boolean persistHistory;

        public Settings(boolean enabled, String baseUrl, String model, int timeoutSeconds,
                int historyMessages, int publicCooldownSeconds, int clanCooldownSeconds,
                int ambientCooldownSeconds, boolean persistHistory) {
            this.enabled = enabled;
            this.baseUrl = baseUrl;
            this.model = model;
            this.timeoutSeconds = timeoutSeconds;
            this.historyMessages = historyMessages;
            this.publicCooldownSeconds = publicCooldownSeconds;
            this.clanCooldownSeconds = clanCooldownSeconds;
            this.ambientCooldownSeconds = ambientCooldownSeconds;
            this.persistHistory = persistHistory;
        }

        public static Settings defaults() {
            return new Settings(true, "http://127.0.0.1:11434", "qwen3.5:4b",
                    30, 8, 4, 5, 18, true);
        }
    }
}
