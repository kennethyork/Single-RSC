package org.nemotech.rsc.bot.scripts;

import org.nemotech.rsc.bot.Bot;
import org.nemotech.rsc.model.GameObject;

/**
 * An agility bot that runs agility courses for XP.
 * 
 * Supported Courses:
 * - Gnome Agility Course (level 1+)
 * - Barbarian Agility Course (level 35+)
 * - Wilderness Agility Course (level 52+)
 * 
 * Usage:
 * 1. Travel to the start of an agility course
 * 2. Start the bot with the course type
 * 3. It will automatically navigate the obstacles
 */
public class AgilityBot extends Bot {
    
    // Gnome Agility Course obstacles (in order)
    private static final int GNOME_BALANCE_LOG = 655;
    private static final int GNOME_NET = 647;
    private static final int GNOME_WATCH_TOWER = 648;
    private static final int GNOME_ROPE_SWING = 650;
    private static final int GNOME_LANDING = 649;
    private static final int GNOME_SECOND_NET = 653;
    private static final int GNOME_PIPE = 654;
    
    private static final int[] GNOME_OBSTACLES = {
        GNOME_BALANCE_LOG, GNOME_NET, GNOME_WATCH_TOWER, 
        GNOME_ROPE_SWING, GNOME_LANDING, GNOME_SECOND_NET, GNOME_PIPE
    };
    
    // Barbarian Agility Course obstacles
    private static final int BARB_SWING = 675;
    private static final int BARB_LOG = 676;
    private static final int BARB_NET = 677;
    private static final int BARB_LEDGE = 678;
    private static final int BARB_LOW_WALL = 163;
    private static final int BARB_LOW_WALL2 = 164;
    private static final int BARB_PIPE = 671;
    private static final int BARB_HANDHOLDS = 679;
    
    private static final int[] BARB_OBSTACLES = {
        BARB_SWING, BARB_LOG, BARB_NET, BARB_LEDGE, 
        BARB_LOW_WALL, BARB_LOW_WALL2, BARB_PIPE, BARB_HANDHOLDS
    };
    
    // Wilderness Agility Course obstacles
    private static final int WILD_GATE = 703;
    private static final int WILD_PIPE = 705;
    private static final int WILD_ROPESWING = 706;
    private static final int WILD_STONE = 707;
    private static final int WILD_LEDGE = 708;
    private static final int WILD_VINE = 709;
    
    private static final int[] WILD_OBSTACLES = {
        WILD_GATE, WILD_PIPE, WILD_ROPESWING, WILD_STONE, WILD_LEDGE, WILD_VINE
    };
    
    public enum Course {
        GNOME,
        BARBARIAN,
        WILDERNESS
    }
    
    private enum State {
        IDLE,
        FINDING_OBSTACLE,
        INTERACTING
    }
    
    private Course currentCourse;
    private State state = State.IDLE;
    private int lapsCompleted = 0;
    private int currentObstacleIndex = 0;
    
    public AgilityBot() {
        super("Agility Bot");
        this.currentCourse = Course.GNOME;
    }
    
    public AgilityBot(Course course) {
        super("Agility Bot");
        this.currentCourse = course;
    }
    
    /**
     * Sets which agility course to run.
     */
    public void setCourse(Course course) {
        this.currentCourse = course;
        this.currentObstacleIndex = 0;
    }
    
    @Override
    public void onStart() {
        super.onStart();
        lapsCompleted = 0;
        currentObstacleIndex = 0;
        state = State.IDLE;
        String courseName;
        int requiredLevel;
        
        switch (currentCourse) {
            case BARBARIAN:
                courseName = "Barbarian";
                requiredLevel = 35;
                break;
            case WILDERNESS:
                courseName = "Wilderness";
                requiredLevel = 52;
                break;
            default:
                courseName = "Gnome";
                requiredLevel = 1;
                break;
        }
        
        gameMessage("Agility bot started! Running " + courseName + " course.");
        
        int agilityLevel = api.getStatCurrent(16); // Agility skill index
        if (agilityLevel < requiredLevel) {
            gameMessage("Warning: " + courseName + " course requires level " + requiredLevel + " Agility!");
        }
    }
    
    @Override
    public void onStop() {
        super.onStop();
        gameMessage("Agility bot stopped. Laps completed: " + lapsCompleted);
    }
    

    private int emptyObstacleSearchCount = 0;

    @Override
    public int loop() {
        // Don't do anything if busy or moving
        if (api.isBusy() || api.isMoving()) {
            return random(300, 500);
        }

        // Handle interacting state reset
        if (state == State.INTERACTING) {
            state = State.IDLE;
        }

        // Find and interact with the next obstacle
        return navigateObstacle();
    }
    
    private int navigateObstacle() {
        int[] obstacles;
        switch (currentCourse) {
            case BARBARIAN:
                obstacles = BARB_OBSTACLES;
                break;
            case WILDERNESS:
                obstacles = WILD_OBSTACLES;
                break;
            default:
                obstacles = GNOME_OBSTACLES;
                break;
        }
        
        // Find closest obstacle from the course
        GameObject nearestObstacle = null;
        int nearestIndex = -1;
        int nearestDist = Integer.MAX_VALUE;
        
        for (int i = 0; i < obstacles.length; i++) {
            GameObject obj = api.getNearestObject(obstacles[i]);
            if (obj != null) {
                int dist = api.distanceTo(obj);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearestObstacle = obj;
                    nearestIndex = i;
                }
            }
        }
        
        if (nearestObstacle == null || nearestObstacle.isRemoved()) {
            state = State.IDLE;
            emptyObstacleSearchCount++;
            
            if (emptyObstacleSearchCount > 5) {
                gameMessage("No obstacles found nearby! Make sure you're at the course.");
                emptyObstacleSearchCount = 0;
            }
            return random(500, 1500);
        }
        
        // Check if we completed a lap (went back to first obstacle)
        if (nearestIndex == 0 && currentObstacleIndex > 0) {
            lapsCompleted++;
            gameMessage("Lap completed! Total laps: " + lapsCompleted);
        }
        currentObstacleIndex = nearestIndex;
        
        // Walk to obstacle if too far
        if (nearestDist > 2) {
            state = State.FINDING_OBSTACLE;
            api.walkTo(nearestObstacle.getX(), nearestObstacle.getY());
            return random(600, 1000);
        }
        
        // Interact with the obstacle
        state = State.INTERACTING;
        api.interactObject(nearestObstacle);
        
        // Wait longer for agility animations
        return random(3000, 5000);
    }
    
    /**
     * Gets the number of laps completed.
     */
    public int getLapsCompleted() {
        return lapsCompleted;
    }
    
    /**
     * Gets the current course being run.
     */
    public Course getCurrentCourse() {
        return currentCourse;
    }
    
    /**
     * Gets the current bot state.
     */
    public State getState() {
        return state;
    }
}
