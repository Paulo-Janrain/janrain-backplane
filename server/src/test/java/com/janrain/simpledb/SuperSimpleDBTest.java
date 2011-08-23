package com.janrain.simpledb;

import org.apache.commons.lang.RandomStringUtils;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.inject.Inject;

import static org.junit.Assert.assertEquals;

/**
 * @author Johnny Bufu
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations = { "classpath:/spring/app-config.xml", "classpath:/spring/mvc-config.xml" })
public class SuperSimpleDBTest {

    @Test
    public void testSimpleDbXmlReservedCharacters() throws Exception {
        testData = new TestNamedMap();
        testData.put("<", "1");
        testData.put(">", "2");
        testData.put("&", "3");
        testData.put("'", "4");
        testData.put("\"", "5");
        testData.put("6", "<");
        testData.put("7", ">");
        testData.put("8", "&");
        testData.put("9", "'");
        testData.put("10", "\"");
        superSimpleDB.store(TEST_TABLE, TestNamedMap.class, testData);
        TestNamedMap retrievedData = superSimpleDB.retrieve(TEST_TABLE, TestNamedMap.class, testData.getName());
        assertEquals(testData, retrievedData);
    }

    @Test
    public void testLongEntries() throws Exception {
        testData = new TestNamedMap();
        for(int i=0; i < 10; i++) {
            testData.put(RandomStringUtils.random(1100), RandomStringUtils.random(2000));
        }
        superSimpleDB.store(TEST_TABLE, TestNamedMap.class, testData, true);
        TestNamedMap retrievedData = superSimpleDB.retrieve(TEST_TABLE, TestNamedMap.class, testData.getName());
        assertEquals(testData, retrievedData);
    }


    @After
    public void tearDown() throws Exception {
        superSimpleDB.drop(TEST_TABLE);
    }

    // - PRIVATE

    @Inject
    private SuperSimpleDB superSimpleDB;

    private static final String TEST_TABLE = "test_" + SuperSimpleDB.class.getSimpleName();

    private TestNamedMap testData;
}
