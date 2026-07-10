package io.github.xinfra.lab.gateway.filter;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class IpFilterTest {

    @Test
    public void testExactIpMatch() {
        Assert.assertTrue(IpFilterGatewayFilterFactory.matchesCidr("192.168.1.1", "192.168.1.1"));
        Assert.assertFalse(IpFilterGatewayFilterFactory.matchesCidr("192.168.1.2", "192.168.1.1"));
    }

    @Test
    public void testCidr24Match() {
        Assert.assertTrue(IpFilterGatewayFilterFactory.matchesCidr("192.168.1.1", "192.168.1.0/24"));
        Assert.assertTrue(IpFilterGatewayFilterFactory.matchesCidr("192.168.1.255", "192.168.1.0/24"));
        Assert.assertFalse(IpFilterGatewayFilterFactory.matchesCidr("192.168.2.1", "192.168.1.0/24"));
    }

    @Test
    public void testCidr16Match() {
        Assert.assertTrue(IpFilterGatewayFilterFactory.matchesCidr("10.0.1.1", "10.0.0.0/16"));
        Assert.assertTrue(IpFilterGatewayFilterFactory.matchesCidr("10.0.255.255", "10.0.0.0/16"));
        Assert.assertFalse(IpFilterGatewayFilterFactory.matchesCidr("10.1.0.1", "10.0.0.0/16"));
    }

    @Test
    public void testCidr8Match() {
        Assert.assertTrue(IpFilterGatewayFilterFactory.matchesCidr("10.1.2.3", "10.0.0.0/8"));
        Assert.assertFalse(IpFilterGatewayFilterFactory.matchesCidr("11.0.0.1", "10.0.0.0/8"));
    }

    @Test
    public void testMatchesAny() {
        Assert.assertTrue(IpFilterGatewayFilterFactory.matchesAny("192.168.1.100",
                Arrays.asList("10.0.0.0/8", "192.168.1.0/24")));
        Assert.assertFalse(IpFilterGatewayFilterFactory.matchesAny("172.16.0.1",
                Arrays.asList("10.0.0.0/8", "192.168.1.0/24")));
    }

    @Test
    public void testInvalidCidr() {
        Assert.assertFalse(IpFilterGatewayFilterFactory.matchesCidr("192.168.1.1", "not-an-ip/24"));
    }

    @Test
    public void testLoopbackMatch() {
        Assert.assertTrue(IpFilterGatewayFilterFactory.matchesCidr("127.0.0.1", "127.0.0.0/8"));
    }
}
