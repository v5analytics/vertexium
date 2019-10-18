package org.vertexium.accumulo.iterator.model.historicalEvents;

import org.apache.accumulo.core.data.ByteSequence;
import org.vertexium.accumulo.iterator.model.Direction;
import org.vertexium.accumulo.iterator.model.ElementType;
import org.vertexium.accumulo.iterator.util.DataInputStreamUtils;
import org.vertexium.accumulo.iterator.util.DataOutputStreamUtils;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public class IteratorHistoricalAddEdgeToVertexEvent extends IteratorHistoricalEvent {
    private final ByteSequence edgeId;
    private final Direction edgeDirection;
    private final String edgeLabel;
    private final String otherVertexId;
    private final ByteSequence edgeVisibility;

    public IteratorHistoricalAddEdgeToVertexEvent(
        String elementId,
        ByteSequence edgeId,
        Direction edgeDirection,
        String edgeLabel,
        String otherVertexId,
        ByteSequence edgeVisibility,
        long timestamp
    ) {
        super(ElementType.VERTEX, elementId, timestamp);
        this.edgeId = edgeId;
        this.edgeDirection = edgeDirection;
        this.edgeLabel = edgeLabel;
        this.otherVertexId = otherVertexId;
        this.edgeVisibility = edgeVisibility;
    }

    public ByteSequence getEdgeId() {
        return edgeId;
    }

    public Direction getEdgeDirection() {
        return edgeDirection;
    }

    public String getEdgeLabel() {
        return edgeLabel;
    }

    public String getOtherVertexId() {
        return otherVertexId;
    }

    public ByteSequence getEdgeVisibility() {
        return edgeVisibility;
    }

    static IteratorHistoricalEvent decode(DataInputStream in, String elementId, long timestamp) throws IOException {
        ByteSequence edgeId = DataInputStreamUtils.decodeByteSequence(in);
        Direction edgeDirection = DataInputStreamUtils.decodeDirection(in);
        String edgeLabel = DataInputStreamUtils.decodeString(in);
        String otherVertexId = DataInputStreamUtils.decodeString(in);
        ByteSequence edgeVisibility = DataInputStreamUtils.decodeByteSequence(in);
        return new IteratorHistoricalAddEdgeToVertexEvent(
            elementId,
            edgeId,
            edgeDirection,
            edgeLabel,
            otherVertexId,
            edgeVisibility,
            timestamp
        );
    }

    @Override
    protected void encode(DataOutputStream out) throws IOException {
        super.encode(out);
        DataOutputStreamUtils.encodeByteSequence(out, getEdgeId());
        DataOutputStreamUtils.encodeDirection(out, getEdgeDirection());
        DataOutputStreamUtils.encodeString(out, getEdgeLabel());
        DataOutputStreamUtils.encodeString(out, getOtherVertexId());
        DataOutputStreamUtils.encodeByteSequence(out, getEdgeVisibility());
    }

    @Override
    protected byte getTypeId() {
        return TYPE_ID_ADD_EDGE_TO_VERTEX;
    }
}
