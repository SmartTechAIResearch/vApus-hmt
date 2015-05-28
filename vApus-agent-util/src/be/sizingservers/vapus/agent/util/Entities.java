/*
 * Copyright 2014 (c) Sizing Servers Lab
 * University College of West-Flanders, Department GKG * 
 * Author(s):
 * 	Dieter Vandroemme
 */
package be.sizingservers.vapus.agent.util;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Date;

/**
 *
 * @author didjeeh
 */
public class Entities implements Serializable {

    private static final long serialVersionUID = 1L;
    
    private long timestamp;
    private ArrayList<Entity> subs;
    
    /**
     * Sets the timestamp to the current date and time.
     */
    public void setTimestamp(){
        this.timestamp = new Date().getTime();
    }
    
    /**
     * Returns the seconds from epoch (1970/1/1). Do not forget to set it first.
     * @return 
     */
    public long getTimestamp(){
        return this.timestamp;
    }
    
        /**
     * Initiates the internal ArrayList if it is null.
     *
     * @return
     */
    public ArrayList<Entity> getSubs() {
        if (this.subs == null) { //Null kvps are not serialized == cleaner output.
            this.subs = new ArrayList<Entity>();
        }
        return this.subs;
    }

    /**
     * Match the name and the Entities if any with the given Entities. The order
     * of Entities and CounterInfos in both collections is not important. You
     * can choose not to match the counters.
     *
     * @param entities
     * @param matchCounters
     * @return
     */
    public boolean match(Entities entities, boolean matchCounters) {
        boolean match = getSubs().size() == entities.getSubs().size();
        if (match) {
            int size = this.subs.size();
            ArrayList<Integer> matched = new ArrayList<Integer>();
            for (int i = 0; i != size; i++) {
                Entity entity = this.subs.get(i);
                for (int j = 0; j != size; j++) {
                    if (!matched.contains(j) && entity.match(entities.subs.get(i), matchCounters)) {
                        matched.add(j);
                    }
                }
            }
            match = size == matched.size();
        }
        return match;
    }

    /**
     * Gets the counters for the CounterInfos at a last level. Null values are
     * included to preserver order, but should not be mixed with real values:
     * use -1 instead if the counter becomes unavailable or invalid. (Counters
     * should only be in leaf nodes.)
     *
     * @return
     * @throws java.lang.Exception
     */
    public ArrayList<String> getCountersLastLevel() throws Exception {
        int level = getLevelCount() - 1;
        if (level < 1 && !getSubs().get(0).isAvailable()) {
            return new ArrayList<String>();
        }

        return getCounters(level);
    }

    /**
     * Gets the counters for the CounterInfos at a given level. Null values are
     * included to preserver order, but should not be mixed with real values:
     * use -1 instead if the counter becomes unavailable or invalid. (Counters
     * should only be in leaf nodes.)
     *
     * @param level
     * @return
     * @throws Exception The given level cannot be smaller than 1.
     */
    public ArrayList<String> getCounters(int level) throws Exception {
        if (level < 1) {
            throw new Exception("The given level cannot be smaller than 1.");
        }
        ArrayList<String> counters = new ArrayList<String>();
        ArrayList<CounterInfo> counterInfos = getCounterInfos(level);
        for (int i = 0; i != counterInfos.size(); i++) {
            counters.add(counterInfos.get(i).getCounter());
        }
        return counters;
    }

    /**
     * Set counters at the deepest level.
     *
     * @param counters Can be an array of anything, for instance String[],
     * something that is not possible in C#.
     */
    public void setCountersLastLevel(Object[] counters) {
        ArrayList<CounterInfo> counterInfos = getCounterInfosAtLastLevel();
        for (int i = 0; i != counterInfos.size(); i++) {
            counterInfos.get(i).setCounter(counters[i]);
        }
    }

    /**
     * Set the counters for the CounterInfos with the same name to the
     * CounterInfos in this. This will happen on all the levels.
     *
     * @param entities
     * @throws java.lang.Exception
     */
    public void setCounters(Entities entities) throws Exception {
        for (int i = 0; i != getSubs().size(); i++) {
            Entity to = this.subs.get(i);
            Entity from = entities.getEntity(to.getName());

            to.setCounters(from);
        }
    }

    /**
     *
     * @param name
     * @return
     */
    public Entity getEntity(String name) {
        for (int i = 0; i != getSubs().size(); i++) {
            Entity entity = this.subs.get(i);
            if (entity.getName().equals(name)) {
                return entity;
            }
        }
        return null;
    }

    /**
     *
     * @param level
     * @param name
     * @return
     * @throws Exception The given level cannot be smaller than 1.
     */
    public CounterInfo getCounterInfo(int level, String name) throws Exception {
        if (level < 1) {
            throw new Exception("The given level cannot be smaller than 1.");
        }

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
     * @return @throws NullPointerException Entity does not exist at the given
     * level. Can happen if not all sub CounterInfo have the same number of
     * levels.
     */
    public ArrayList<CounterInfo> getCounterInfosAtLastLevel() throws NullPointerException {
        try {
            return getCounterInfos(getLevelCount() - 1);
        } catch (NullPointerException ex) {
            throw ex;
        } catch (Exception ex) {
            //Cannot occur.
        }
        return null;
    }

    /**
     *
     * @param level The minimum level == 1, since level 0 == the root level.
     * @return
     * @throws NullPointerException CounterInfos does not existat the given
     * level. Can happen if not all sub CounterInfos have the same number of
     * levels.
     * @throws Exception The given level cannot be smaller than 1.
     */
    public ArrayList<CounterInfo> getCounterInfos(int level) throws NullPointerException, Exception {
        if (level < 1) {
            throw new Exception("The given level cannot be smaller than 1.");
        }

        int givenLevel = level;
        ArrayList<CounterInfo> counterInfos = new ArrayList<CounterInfo>();

        --level;
        for (int i = 0; i != getSubs().size(); i++) {
            ArrayList<CounterInfo> subCounterInfos = this.subs.get(i).getCounterInfos(level);
            if (this.subs.get(i).isAvailable() && subCounterInfos.isEmpty()) {
                throw new NullPointerException("CounterInfos does not exist at the given level (" + givenLevel + ").");
            } else {
                counterInfos.addAll(subCounterInfos);
            }
        }
        return counterInfos;
    }

    /**
     * Providing that all subs have the same number of levels.
     *
     * @return 0 if not subs.
     */
    public int getLevelCount() {
        int levelCount = 0;
        if (!getSubs().isEmpty()) {
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
        int deepCount = getSubs().size();
        for (int i = 0; i != this.subs.size(); i++) {
            deepCount += this.subs.get(i).getDeepCount();
        }
        return deepCount;
    }

    /**
     * Validates the counters in all CounterInfos. Returns warnings or throws an
     * Exception if counter values are mixed with null values in the
     * CounterInfos of the last level.
     *
     * @return warnings or an empty string.
     * @throws java.lang.Exception
     */
    public String validateCounters() throws Exception {
        StringBuilder sb = new StringBuilder();

        int levelCount = getLevelCount();
        int lastLevel = levelCount - 1;
        for (int i = 1; i < lastLevel; i++) {
            ArrayList<String> counters = getCounters(i);

            for (int j = 0; j != counters.size(); j++) {
                if (counters.get(j) != null) {
                    sb.append("WARNING: counters found at level ");
                    sb.append(j);
                    sb.append(". Counters should only reside at the last level: ");
                    sb.append(lastLevel);
                    sb.append('\n');
                }
            }
        }

        if (lastLevel > 0) {
            ArrayList<String> counters = getCounters(lastLevel);

            boolean nullFound = false, notNullFound = false;
            for (int j = 0; j != counters.size(); j++) {
                if (counters.get(j) == null) {
                    nullFound = true;
                } else {
                    notNullFound = true;
                }

                if (nullFound && notNullFound) {
                    throw new Exception("Counter values are mixed with null values in the CounterInfos of the last level. If a counter value becomes unavailable or invalid, use -1 instead of null.");
                }
            }
        }

        return sb.toString();
    }

    /**
     * Duplicate Entity names and CounterInfos names on the same level should
     * not occur.
     *
     * @return
     */
    public boolean hasDuplicateNames() {
        ArrayList<String> names = new ArrayList<String>();
        for (int i = 0; i != getSubs().size(); i++) {
            Entity entity = this.subs.get(i);
            if (names.contains(entity.getName())) {
                return true;
            }
            names.add(entity.getName());

            if (entity.hasDuplicateNames()) {
                return true;
            }
        }
        return false;
    }

    /**
     * We do not use the buggy java clone thing, instead we use this.
     *
     * @return
     */
    public Entities safeClone() {
        Entities clone = new Entities();
        for (int i = 0; i != getSubs().size(); i++) {
            clone.getSubs().add(this.subs.get(i).safeClone());
        }
        return clone;
    }
}
