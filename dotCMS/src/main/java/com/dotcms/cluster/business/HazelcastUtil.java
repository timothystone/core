package com.dotcms.cluster.business;

import com.dotcms.cluster.business.HazelcastUtil.HazelcastInstanceType;
import com.dotmarketing.util.Config;
import com.dotmarketing.util.Logger;
import com.dotmarketing.util.UtilMethods;
import com.dotmarketing.util.WebKeys;
import com.hazelcast.config.XmlConfigBuilder;
import com.hazelcast.core.Hazelcast;
import com.hazelcast.core.HazelcastInstance;

import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by jasontesser on 4/5/17.
 */
public class HazelcastUtil {

	private final static String PROPERTY_HAZELCAST_NETWORK_BIND_ADDRESS = "HAZELCAST_NETWORK_BIND_ADDRESS";
	private final static String PROPERTY_HAZELCAST_NETWORK_BIND_PORT = "HAZELCAST_NETWORK_BIND_PORT";
	private final static String PROPERTY_HAZELCAST_NETWORK_TCP_MEMBERS = "HAZELCAST_NETWORK_TCP_MEMBERS";

	public enum HazelcastInstanceType {
	    EMBEDDED("hazelcast-embedded.xml", true),
	    CLIENT("hazelcast-client.xml", false);

	    private String path;
	    private boolean autoWired;

	    HazelcastInstanceType(String path, boolean autoWired) {
	        this.path = path;
	        this.autoWired = autoWired;
	    }

	    public String getPath() {
	        return path;
	    }

	    public boolean isAutoWired() {
	        return autoWired;
	    }
	}

    private static Map<HazelcastInstanceType, HazelcastInstance> _memberInstances = new HashMap<>();

    final String syncMe = "hazelSync";

    public HazelcastInstance getHazel(HazelcastInstanceType instanceType){
        try{
            initMember(instanceType);
        }catch (Exception e) {
            Logger.error(HazelcastUtil.class, "Could not initialize Hazelcast Member", e);
        }
        return _memberInstances.get(instanceType);
    }

    public void shutdown(HazelcastInstanceType instanceType){
        if (_memberInstances.get(instanceType) != null) {
            synchronized (syncMe) {
                if (_memberInstances.get(instanceType) != null) {
                	_memberInstances.get(instanceType).shutdown();
                	_memberInstances.remove(instanceType);
                }
            }
        }
    }

    public void shutdown() {
        for(HazelcastInstanceType instanceType : HazelcastInstanceType.values()){
        	shutdown(instanceType);        	
        }
    }

    private void initMember(HazelcastInstanceType instanceType) {

        if (_memberInstances.get(instanceType) == null) {
            long start = System.currentTimeMillis();
            synchronized (syncMe) {
                if (_memberInstances.get(instanceType) == null) {
                    Logger.info(this, "Setting Up HazelCast ("+ instanceType +")");

                    com.hazelcast.config.Config config = buildConfig(instanceType);

                    HazelcastInstance memberInstance = Hazelcast.newHazelcastInstance(config);

                    _memberInstances.put(instanceType, memberInstance);

        		    Logger.info(this, "Initialized Hazelcast member "+ memberInstance);

        		    try {
        		    	throw new Exception();
        		    } catch(Exception e) {
        		    	StringWriter sw = new StringWriter();
        		    	e.printStackTrace(new PrintWriter(sw));
        		    	Logger.info(this, "Location:"+ sw.toString());
        		    }
                }
            }
            System.setProperty(WebKeys.DOTCMS_STARTUP_TIME_HAZEL, String.valueOf(System.currentTimeMillis() - start));
        }
    }

	private com.hazelcast.config.Config buildConfig(HazelcastInstanceType instanceType) {

		InputStream is = getClass().getClassLoader().getResourceAsStream(instanceType.getPath());

		try {
		    XmlConfigBuilder builder = new XmlConfigBuilder(is);

		    com.hazelcast.config.Config config = builder.build();

		    if (instanceType.isAutoWired() && Config.getBooleanProperty("CLUSTER_HAZELCAST_AUTOWIRE", true)) {

		    	Map<String, Object> properties = buildProperties();

			    if (UtilMethods.isSet( properties.get(PROPERTY_HAZELCAST_NETWORK_BIND_PORT) ) &&
			    	UtilMethods.isSet( properties.get(PROPERTY_HAZELCAST_NETWORK_TCP_MEMBERS) ) )
			    {
				    config.getNetworkConfig().getJoin().getTcpIpConfig().setEnabled(true);

				    config.getNetworkConfig().setPort(Integer.parseInt((String) properties.get(PROPERTY_HAZELCAST_NETWORK_BIND_PORT)));

			        for(String member : (String[]) properties.get(PROPERTY_HAZELCAST_NETWORK_TCP_MEMBERS)) {
			        	config.getNetworkConfig().getJoin().getTcpIpConfig().addMember(member);
			        }
			    }
		    }

		    return config;
		} finally {
	        try {
	            is.close();
	        }catch (Exception e){
	            Logger.error(Hazelcast.class, "Unable to close inputstream for Hazel xml", e);
	        }
		}
	}

    private Map<String, Object> buildProperties(){
        Map<String, Object> properties = new HashMap<>();

        // Bind Address
        String bindAddressProperty = Config.getStringProperty(WebKeys.DOTCMS_CACHE_TRANSPORT_BIND_ADDRESS, null);
        if (UtilMethods.isSet(bindAddressProperty)) {
        	properties.put(HazelcastUtil.PROPERTY_HAZELCAST_NETWORK_BIND_ADDRESS, bindAddressProperty);
        }

        // Bind Port
        String bindPortProperty = Config.getStringProperty(WebKeys.DOTCMS_CACHE_TRANSPORT_BIND_PORT, null);
        if (UtilMethods.isSet(bindPortProperty)) {
        	properties.put(HazelcastUtil.PROPERTY_HAZELCAST_NETWORK_BIND_PORT, bindPortProperty);
        }

        // Initial Hosts
        String initialHostsProperty = Config.getStringProperty(WebKeys.DOTCMS_CACHE_TRANSPORT_TCP_INITIAL_HOSTS, null);
        if (UtilMethods.isSet(initialHostsProperty)) {

        	String[] initialHosts = initialHostsProperty.split(",");

        	for(int i = 0; i < initialHosts.length; i++){
				String initialHost = initialHosts[i].trim();

				initialHosts[i] = initialHost.replaceAll("^(.*)\\[(.*)\\]$", "$1:$2");
			}

        	properties.put(HazelcastUtil.PROPERTY_HAZELCAST_NETWORK_TCP_MEMBERS, initialHosts);
        }

        return properties;
    }
}
