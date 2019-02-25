package org.vertexium.serializer.kryo;

import org.vertexium.GraphConfiguration;
import org.vertexium.VertexiumException;
import org.vertexium.VertexiumSerializer;
import org.vertexium.serializer.kryo.quickSerializers.*;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

public class QuickKryoVertexiumSerializer implements VertexiumSerializer {
    private static final byte[] EMPTY = new byte[0];
    public static final String CONFIG_COMPRESS = GraphConfiguration.SERIALIZER + ".enableCompression";
    public static final boolean CONFIG_COMPRESS_DEFAULT = false;
    private final boolean enableCompression;
    private QuickTypeSerializer defaultQuickTypeSerializer = new KryoQuickTypeSerializer();
    private Map<Class, QuickTypeSerializer> quickTypeSerializersByClass = new HashMap<>();
    private Map<Byte, QuickTypeSerializer> quickTypeSerializersByMarker = new HashMap<>();

    public QuickKryoVertexiumSerializer(GraphConfiguration config) {
        this(config.getBoolean(CONFIG_COMPRESS, CONFIG_COMPRESS_DEFAULT));
    }

    public QuickKryoVertexiumSerializer(boolean enableCompression) {
        this.enableCompression = enableCompression;

        quickTypeSerializersByClass.put(String.class, new StringQuickTypeSerializer());
        quickTypeSerializersByClass.put(Long.class, new LongQuickTypeSerializer());
        quickTypeSerializersByClass.put(Date.class, new DateQuickTypeSerializer());
        quickTypeSerializersByClass.put(Double.class, new DoubleQuickTypeSerializer());
        quickTypeSerializersByClass.put(BigDecimal.class, new BigDecimalQuickTypeSerializer());

        quickTypeSerializersByMarker.put(QuickTypeSerializer.MARKER_KRYO, new KryoQuickTypeSerializer());
        quickTypeSerializersByMarker.put(QuickTypeSerializer.MARKER_STRING, new StringQuickTypeSerializer());
        quickTypeSerializersByMarker.put(QuickTypeSerializer.MARKER_LONG, new LongQuickTypeSerializer());
        quickTypeSerializersByMarker.put(QuickTypeSerializer.MARKER_DATE, new DateQuickTypeSerializer());
        quickTypeSerializersByMarker.put(QuickTypeSerializer.MARKER_DOUBLE, new DoubleQuickTypeSerializer());
        quickTypeSerializersByMarker.put(QuickTypeSerializer.MARKER_BIG_DECIMAL, new BigDecimalQuickTypeSerializer());
    }

    @Override
    public byte[] objectToBytes(Object object) {
        if (object == null) {
            return EMPTY;
        }
        QuickTypeSerializer quickTypeSerializer = quickTypeSerializersByClass.get(object.getClass());
        byte[] bytes;
        if (quickTypeSerializer != null) {
            bytes = quickTypeSerializer.objectToBytes(object);
        } else {
            bytes = defaultQuickTypeSerializer.objectToBytes(object);
        }
        return compress(bytes);
    }

    @Override
    public <T> T bytesToObject(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        bytes = expand(bytes);
        QuickTypeSerializer quickTypeSerializer = quickTypeSerializersByMarker.get(bytes[0]);
        if (quickTypeSerializer != null) {
            return quickTypeSerializer.valueToObject(bytes);
        }
        throw new VertexiumException("Invalid marker: " + Integer.toHexString(bytes[0]));
    }

    protected byte[] compress(byte[] bytes) {
        if (!enableCompression) {
            return bytes;
        }

        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            deflater.setInput(bytes);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(bytes.length);
            deflater.finish();
            byte[] buffer = new byte[1024];
            while (!deflater.finished()) {
                int count = deflater.deflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            outputStream.close();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new VertexiumException("Could not compress bytes", ex);
        } finally {
            deflater.end();
        }
    }

    protected byte[] expand(byte[] bytes) {
        if (!enableCompression) {
            return bytes;
        }

        Inflater inflater = new Inflater();
        try {
            inflater.setInput(bytes);
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream(bytes.length);
            byte[] buffer = new byte[1024];
            while (!inflater.finished()) {
                int count = inflater.inflate(buffer);
                outputStream.write(buffer, 0, count);
            }
            outputStream.close();

            return outputStream.toByteArray();
        } catch (Exception ex) {
            throw new VertexiumException("Could not decompress bytes", ex);
        } finally {
            inflater.end();
        }
    }
}
