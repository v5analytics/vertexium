package org.vertexium.util;

import com.google.common.collect.Lists;
import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

public class ObjectUtilsTest {
    @Test
    public void testCompare() {
        assertEquals(0, ObjectUtils.compare(null, null));
        assertEquals(1, ObjectUtils.compare(null, 1));
        assertEquals(-1, ObjectUtils.compare(1, null));

        ArrayList<Integer> list = Lists.newArrayList(1, null);
        list.sort(ObjectUtils::compare);
        assertEquals(1, (int) list.get(0));
        assertEquals(null, list.get(1));
    }
}
