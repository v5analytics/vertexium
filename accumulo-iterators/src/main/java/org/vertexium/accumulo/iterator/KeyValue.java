package org.vertexium.accumulo.iterator;

import org.apache.accumulo.core.data.ArrayByteSequence;
import org.apache.accumulo.core.data.ByteSequence;
import org.apache.accumulo.core.data.Key;
import org.apache.accumulo.core.data.Value;
import org.apache.hadoop.io.Text;
import org.vertexium.accumulo.iterator.util.ByteSequenceUtils;

public class KeyValue {
    private ByteSequence columnFamilyData;
    private ByteSequence columnQualifierData;
    private Key key;
    private Value value;

    public void set(Key key, Value value) {
        this.key = key;
        this.value = value;
        this.columnFamilyData = key.getColumnFamilyData();
        this.columnQualifierData = key.getColumnQualifierData();
    }

    public Text takeRow() {
        return key.getRow();
    }

    public boolean columnFamilyEquals(byte[] bytes) {
        return ByteSequenceUtils.equals(columnFamilyData, bytes);
    }

    public boolean columnQualifierEquals(byte[] bytes) {
        return ByteSequenceUtils.equals(columnQualifierData, bytes);
    }

    public Text takeColumnQualifier() {
        return key.getColumnQualifier();
    }

    public Text takeColumnVisibility() {
        return key.getColumnVisibility();
    }

    public long getTimestamp() {
        return key.getTimestamp();
    }

    public Value peekValue() {
        return value;
    }

    public Value takeValue() {
        return new Value(value);
    }

    public Key peekKey() { return key; }

    public ByteSequence takeColumnQualifierByteSequence() {
        return new ArrayByteSequence(takeColumnQualifier().getBytes());
    }

    public ByteSequence takeColumnVisibilityByteSequence() {
        return new ArrayByteSequence(takeColumnVisibility().getBytes());
    }

    public ByteSequence peekColumnVisibilityByteSequence() {
        return key.getColumnVisibilityData();
    }
}
