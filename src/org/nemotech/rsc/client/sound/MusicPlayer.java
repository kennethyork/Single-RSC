package org.nemotech.rsc.client.sound;

import java.io.File;
import java.io.FilenameFilter;

import javax.sound.midi.Sequencer;

import org.nemotech.rsc.client.mudclient;
import org.nemotech.rsc.Constants;
import org.nemotech.rsc.util.Util;

public class MusicPlayer {
    
    /* Sequencer intentionally unused; audio is disabled */
    private Sequencer sequencer;
    private String currentSong;
    private boolean paused = false;
    private boolean running = false;
    
    public MusicPlayer() {
        /* Audio disabled: do not initialize a MIDI sequencer */
        sequencer = null;
        currentSong = null;
        paused = false;
        running = false;
    }
    
    public boolean isPaused() {
        return paused;
    }
    
    public void pause() {
        paused = true;
        running = false;
    }
    
    public void resume() {
        paused = false;
    }
    
    public String getCurrentSong() {
        return currentSong;
    }
    
    public void startRandom() {
        /* No-op: music is disabled */
    }
    
    public void start(String fileName) {
        /* No-op: track the selection but do not play */
        currentSong = fileName;
        mudclient.getInstance().selectedSong = fileName;
        running = false;
        paused = false;
    }

    public void stop() {
        /* No-op */
        running = false;
        paused = false;
    }
    
    public boolean isRunning() {
        return running;
    }
    
    public void close() {
        /* No-op */
    }

}
