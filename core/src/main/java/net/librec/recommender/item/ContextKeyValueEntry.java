package net.librec.recommender.item;

import java.util.Objects;

/**
 * Created by wkq on 13/05/2017.
 */
public class ContextKeyValueEntry {
    private int contextIdx;
    private int key;
    private double value;
    private int keyIdx;

    public ContextKeyValueEntry() {

    }

    public ContextKeyValueEntry(int contextIdx, int key, double value, int keyIdx) {
        this.contextIdx = contextIdx;
        this.key = key;
        this.value = value;
        this.keyIdx = keyIdx;
    }

    /**
     * @return the context index
     */
    public int getContextIdx() {
        return contextIdx;
    }

    /**
     * @param contextIdx the context index to set
     */
    public void setContextIdx(int contextIdx) {
        this.contextIdx = contextIdx;
    }

    /**
     * @return the key
     */
    public int getKey() {
        return key;
    }

    /**
     * @param key the key to set
     */
    public void setKey(int key) {
        this.key = key;
    }

    /**
     * @return the value
     */
    public double getValue() {
        return value;
    }

    /**
     * @param value the value to set
     */
    public void setValue(double value) {
        this.value = value;
    }

    public int getKeyIdx() {
        return keyIdx;
    }

    public void setKeyIdx(int keyIdx) {
        this.keyIdx = keyIdx;
    }

    //TODO: change from original

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ContextKeyValueEntry entry = (ContextKeyValueEntry) o;
        return getContextIdx() == entry.getContextIdx() &&
                getKey() == entry.getKey() &&
                getKeyIdx() == entry.getKeyIdx();
    }

    @Override
    public int hashCode() {
        return Objects.hash(getContextIdx(), getKey(), getKeyIdx());
    }
    @Override
    public String toString(){
        return "ContextIdx: "+getContextIdx()+"\tKey: " + getKey()+ "\tKeyIdx: "+getKeyIdx();
    }
}
