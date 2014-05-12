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
 * A wdyh (what do you have) counterInfo to serialize from and to (used lib:
 * gson-2.2.4.jar). Sub CounterInfos can be added. If a CounterInfo is a
 * 'leafnode', it can have a counter value (type String to be able to
 * deserialize).
 *
 * @author didjeeh
 */
public class CounterInfo implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String name;
    private String counter;
    private ArrayList<CounterInfo> subs;

    /**
     *
     * @param name Should be unique for the level this CounterInfo is at.
     */
    public CounterInfo(String name) {
        this.name = name;
    }

    /**
     *
     * @param name Should be unique for the level this CounterInfo is at.
     * @param counter The toString() is stored.
     */
    public CounterInfo(String name, Object counter) {
        this.name = name;
        if (counter != null) {
            this.counter = counter.toString();
        }
    }

    /**
     * Should be unique for the level this CounterInfo is at.
     *
     * @return
     */
    public String getName() {
        return this.name;
    }

    /**
     *
     * @return
     */
    public String getCounter() {
        return this.counter;
    }

    /**
     *
     * @param counter
     */
    public void setCounter(Object counter) {
        if (counter == null) {
            this.counter = null;
        } else {
            this.counter = counter.toString();
        }
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
     * Match the name and the subs if any with the given CounterInfo. The order
     * of CounterInfos in both collections is not important. You can choose not
     * to match the counter.
     *
     * @param counterInfo
     * @param matchCounter
     * @return
     */
    protected boolean match(CounterInfo counterInfo, boolean matchCounter) {
        boolean match = this.name.equals(counterInfo.name);
        if (match && matchCounter) {
            match = this.counter == null && counterInfo.counter == null;
            if (!match && this.counter != null && counterInfo.counter != null) {
                match = this.counter.equals(counterInfo.counter);
            }
        }

        if (match) {
            if (this.subs != null && counterInfo.subs != null && this.subs.size() == counterInfo.subs.size()) {
                int size = this.subs.size();
                ArrayList<Integer> matched = new ArrayList<Integer>();
                for (int i = 0; i != size; i++) {
                    CounterInfo sub = this.subs.get(i);
                    for (int j = 0; j != size; j++) {
                        if (!matched.contains(j) && sub.match(counterInfo.subs.get(i), matchCounter)) {
                            matched.add(j);
                        }
                    }
                }
                match = size == matched.size();
            } else if (this.subs == null && counterInfo.subs == null) {
            } else {
                match = false;
            }
        }

        return match;
    }

    /**
     * Set the counters for the CounterInfo with the same name to this.
     *
     * @param counterInfo
     * @throws java.lang.Exception
     */
    protected void setCounters(CounterInfo counterInfo) throws Exception {
        if (subs != null && counterInfo.subs != null) {
            for (int i = 0; i != this.subs.size(); i++) {
                CounterInfo to = this.subs.get(i);
                CounterInfo from = counterInfo.getCounterInfo(0, to.name);

                to.setCounters(from);
            }
        } else {
            this.counter = counterInfo.counter;
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
            if (counterInfo.name.equals(name)) {
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
     * level. Can happen if not all subheaders have the same number of levels.
     * @throws Exception The given level cannot be smaller than 0.
     */
    protected ArrayList<CounterInfo> getCounterInfos(int level) throws NullPointerException, Exception {
        if (level < 0) {
            throw new Exception("The given level cannot be smaller than 0.");
        }

        ArrayList<CounterInfo> counterInfos = new ArrayList<CounterInfo>();
        if (this.subs != null) {
            if (level == 0) {
                counterInfos.addAll(this.subs);
            } else {
                --level;
                for (int i = 0; i != this.subs.size(); i++) {
                    ArrayList<CounterInfo> subCounterInfos = this.subs.get(i).getCounterInfos(level);
                    if (subCounterInfos.isEmpty()) {
                        throw new NullPointerException("CounterInfos does not exist at the given level.");
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
    protected int getDeepCount() {
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
     * Duplicate CounterInfo names on the same level should not occur.
     *
     * @return
     */
    protected boolean hasDuplicateNames() {
        if (this.subs != null) {
            ArrayList<String> names = new ArrayList<String>();
            for (int i = 0; i != this.subs.size(); i++) {
                CounterInfo sub = this.subs.get(i);
                if (names.contains(sub.name)) {
                    return true;
                }
                names.add(sub.name);

                if (sub.hasDuplicateNames()) {
                    return true;
                }
            }
        }
        return false;
    }

    protected CounterInfo safeClone() {
        CounterInfo clone = new CounterInfo(this.name, this.counter);
        if (this.subs != null) {
            for (int i = 0; i != this.subs.size(); i++) {
                clone.getSubs().add(this.subs.get(i).safeClone());
            }
        }
        return clone;
    }
}
