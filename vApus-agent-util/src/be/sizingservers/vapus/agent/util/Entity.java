/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent.util;

import java.io.Serializable;
import java.util.ArrayList;

/**
 *
 * @author didjeeh
 */
public class Entity implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private final boolean isAvailable;
    private ArrayList<CounterInfo> subs;

    /**
     *
     * @param name Should be unique. Is for instance a machine name.
     * @param isAvailable Ex: Is the machine to monitor powered on.
     */
    public Entity(String name, boolean isAvailable) {
        this.name = name;
        this.isAvailable = isAvailable;
    }

    /**
     * Should be unique.
     *
     * @return
     */
    public String getName() {
        return this.name;
    }

    /**
     * Ex: Is the machine to monitor powered on.
     *
     * @return
     */
    public boolean isAvailable() {
        return this.isAvailable;
    }

    /**
     * Initiates the internal ArrayList if it is null.
     *
     * @return
     */
    public ArrayList<CounterInfo> getSubs() {
        if (this.subs == null) { //Null kvps are not serialized == cleaner output.
            this.subs = new ArrayList<CounterInfo>();
        }
        return this.subs;
    }

    /**
     * Match the name and the subs if any with the given CounterInfos. The order
     * of CounterInfos in both collections is not important. You can choose not
     * to match the counters.
     *
     * @param entity
     * @param matchCounters
     * @return
     */
    protected boolean match(Entity entity, boolean matchCounters) {
        boolean match = this.name.equals(entity.name);
        if (match) {
            if (this.subs != null && entity.subs != null && this.subs.size() == entity.subs.size()) {
                int size = this.subs.size();
                ArrayList<Integer> matched = new ArrayList<Integer>();
                for (int i = 0; i != size; i++) {
                    CounterInfo counterInfo = this.subs.get(i);
                    for (int j = 0; j != size; j++) {
                        if (!matched.contains(j) && counterInfo.match(entity.subs.get(i), matchCounters)) {
                            matched.add(j);
                        }
                    }
                }
                match = size == matched.size();
            } else if (this.subs == null && entity.subs == null) {
            } else {
                match = false;
            }
        }
        return match;
    }

    /**
     * Set the counters for the CounterInfos with the same name to the
     * CounterInfos in this. This will happen on all the levels.
     *
     * @param entity
     * @throws java.lang.Exception
     */
    protected void setCounters(Entity entity) throws Exception {
        if (this.subs != null && entity.subs != null) {
            for (int i = 0; i != this.subs.size(); i++) {
                CounterInfo to = this.subs.get(i);
                CounterInfo from = entity.getCounterInfo(0, to.getName());

                to.setCounters(from);
            }
        }
    }

    /**
     *
     * @param level
     * @param name
     * @return
     * @throws Exception
     */
    private CounterInfo getCounterInfo(int level, String name) throws Exception {
        ArrayList<CounterInfo> counterInfos = getCounterInfos(level);
        for (int i = 0; i != counterInfos.size(); i++) {
            CounterInfo counterInfo = counterInfos.get(i);
            if (counterInfo.getName().equals(name)) {
                return counterInfo;
            }
        }
        return null;
    }

    /**
     *
     * @param level
     * @return
     * @throws NullPointerException CounterInfos does not exist at the given
     * level. Can happen if not all sub CounterInfos have the same number of
     * levels.
     * @throws Exception The given level cannot be smaller than 0.
     */
    protected ArrayList<CounterInfo> getCounterInfos(int level) throws NullPointerException, Exception {
        if (level < 0) {
            throw new Exception("The given level cannot be smaller than 0.");
        }

        int givenLevel = level;
        ArrayList<CounterInfo> counterInfos = new ArrayList<CounterInfo>();
        if (this.subs != null) {
            if (level == 0) {
                counterInfos.addAll(this.subs);
            } else {
                --level;
                for (int i = 0; i != this.subs.size(); i++) {
                    ArrayList<CounterInfo> subCounterInfos = this.subs.get(i).getCounterInfos(level);
                    if (subCounterInfos.isEmpty()) {
                        throw new NullPointerException("CounterInfos does not exist at the given level (" + givenLevel + ").");
                    } else {
                        counterInfos.addAll(subCounterInfos);
                    }
                }
            }
        }
        return counterInfos;
    }

    /**
     * Providing that all sub CounterInfos have the same number of levels.
     *
     * @return 0 if not subs.
     */
    protected int getLevelCount() {
        int levelCount = 0;
        if (this.subs != null && !this.subs.isEmpty()) {
            levelCount = 1;
            levelCount += this.subs.get(0).getLevelCount();
        }
        return levelCount;
    }

    /**
     * The count of all CounterInfos on all levels.
     *
     * @return
     */
    public int getDeepCount() {
        int deepCount = 0;
        if (this.subs != null) {
            deepCount = this.subs.size();
            for (int i = 0; i != this.subs.size(); i++) {
                deepCount += this.subs.get(i).getDeepCount();
            }
        }
        return deepCount;
    }

    /**
     * Duplicate CounterInfos names on the same level should not occur.
     *
     * @return
     */
    protected boolean hasDuplicateNames() {
        if (this.subs != null) {
            ArrayList<String> names = new ArrayList<String>();
            for (int i = 0; i != this.subs.size(); i++) {
                CounterInfo counterInfo = this.subs.get(i);
                if (names.contains(counterInfo.getName())) {
                    return true;
                }
                names.add(counterInfo.getName());

                if (counterInfo.hasDuplicateNames()) {
                    return true;
                }
            }
        }
        return false;
    }

    protected Entity safeClone() {
        Entity clone = new Entity(this.name, this.isAvailable);
        if (this.subs != null) {
            for (int i = 0; i != this.subs.size(); i++) {
                clone.getSubs().add(this.subs.get(i).safeClone());
            }
        }
        return clone;
    }
}
