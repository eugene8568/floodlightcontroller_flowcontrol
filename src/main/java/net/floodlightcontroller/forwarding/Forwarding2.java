package net.floodlightcontroller.forwarding;
 

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
 
import org.projectfloodlight.openflow.protocol.OFFactories;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.protocol.OFFlowModCommand;
import org.projectfloodlight.openflow.protocol.OFFlowModFlags;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFPacketIn;
import org.projectfloodlight.openflow.protocol.OFPacketOut;
import org.projectfloodlight.openflow.protocol.OFPortDesc;
import org.projectfloodlight.openflow.protocol.OFType;
import org.projectfloodlight.openflow.protocol.OFVersion;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.Match.Builder;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.EthType;
import org.projectfloodlight.openflow.types.IPv4Address;
import org.projectfloodlight.openflow.types.IpProtocol;
import org.projectfloodlight.openflow.types.MacAddress;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.OFPort;
import org.projectfloodlight.openflow.types.TransportPort;
import org.projectfloodlight.openflow.types.U64;
 
import net.floodlightcontroller.core.FloodlightContext;
import net.floodlightcontroller.core.IFloodlightProviderService;
import net.floodlightcontroller.core.IOFMessageListener;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.IListener.Command;
import net.floodlightcontroller.core.IOFSwitchListener;
import net.floodlightcontroller.core.PortChangeType;
import net.floodlightcontroller.core.internal.IOFSwitchManager;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.FloodlightModuleException;
import net.floodlightcontroller.core.module.IFloodlightModule;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.core.util.AppCookie;
import net.floodlightcontroller.core.web.ControllerSwitchesResource.DatapathIDJsonSerializerWrapper;
import net.floodlightcontroller.core.web.serializers.DPIDSerializer;
import net.floodlightcontroller.packet.ARP;
import net.floodlightcontroller.packet.Data;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;
import net.floodlightcontroller.packet.TCP;
import net.floodlightcontroller.packet.UDP;
import net.floodlightcontroller.routing.IRoutingDecision;
import net.floodlightcontroller.routing.RoutingDecision;
import net.floodlightcontroller.staticflowentry.IStaticFlowEntryPusherService;
import net.floodlightcontroller.util.FlowModUtils;
import net.floodlightcontroller.util.MatchUtils;
import net.floodlightcontroller.util.OFDPAUtils;
import net.floodlightcontroller.util.OFMessageDamper;

import org.restlet.resource.Get;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
 
public class Forwarding2 implements IOFSwitchListener, IOFMessageListener, IFloodlightModule,IStaticFlowEntryPusherService{
    protected IStaticFlowEntryPusherService aa;
	protected IFloodlightProviderService floodlightProvider;
	protected IOFSwitchService switchService;
	protected static Logger log = LoggerFactory.getLogger(Forwarding2.class);
	protected OFMessageDamper messageDamper;
	
	protected static int OFMESSAGE_DAMPER_CAPACITY = 10000; // TODO: find sweet spot
	protected static int OFMESSAGE_DAMPER_TIMEOUT = 250; // ms
	public static int FLOWMOD_DEFAULT_PRIORITY = 1; // 0 is the default table-miss flow in OF1.3+, so we need to use 1
	
	// flow-mod - for use in the cookie
	public static final int FORWARDING_APP_ID = 2; // TODO: This must be managed
	//IOFSwitch mySwitch = switchService.getSwitch(DatapathId.of("00:00:00:00:00:00:00:04"));
	
	
    @Override
    public String getName() {
        // TODO Auto-generated method stub
        return "forwarding2";
    }
 
    @Override
	public boolean isCallbackOrderingPrereq(OFType type, String name) {
		// TODO Auto-generated method stub
		System.out.println("--------------PRE ORDERING IN FORWARDING 2--------------"+"\n");
		return (type.equals(OFType.PACKET_IN) && (name.equals("topology") || name.equals("devicemanager")));
		//return false;
	}

	@Override
	public boolean isCallbackOrderingPostreq(OFType type, String name) {
		// TODO Auto-generated method stub
		System.out.println("--------------POST ORDERING IN FORWARDING 2--------------"+"\n");
		if(type==OFType.PACKET_IN){
			return false;
			//return true;
		}
		return (type.equals(OFType.PACKET_IN) && (name.equals("forwarding")));
		
	}
 
    @Override
    public Collection<Class<? extends IFloodlightService>> getModuleServices() {
        // TODO Auto-generated method stub
        return null;
    }
 
    @Override
    public Map<Class<? extends IFloodlightService>, IFloodlightService> getServiceImpls() {
        // TODO Auto-generated method stub
        return null;
    }
 
    @Override
	public Collection<Class<? extends IFloodlightService>> getModuleDependencies() {
		// TODO Auto-generated method stub
		Collection<Class<? extends IFloodlightService>> l =
				new ArrayList<Class<? extends IFloodlightService>>();
		l.add(IFloodlightProviderService.class);
		l.add(IOFSwitchService.class);
	    l.add(IStaticFlowEntryPusherService.class);
		return l;
	}
 
    @Override
	public void init(FloodlightModuleContext context) throws FloodlightModuleException {
		// TODO Auto-generated method stub
		System.out.println("RUN Forwarding 2 ..................");
      floodlightProvider = context.getServiceImpl(IFloodlightProviderService.class);
		switchService = context.getServiceImpl(IOFSwitchService.class);
	       aa = context.getServiceImpl(IStaticFlowEntryPusherService.class);
		messageDamper = new OFMessageDamper(OFMESSAGE_DAMPER_CAPACITY,
				EnumSet.of(OFType.FLOW_MOD),
				OFMESSAGE_DAMPER_TIMEOUT);
	}

 
    @Override
	public void startUp(FloodlightModuleContext context)
			throws FloodlightModuleException {
		// TODO Auto-generated method stub
		floodlightProvider.addOFMessageListener(OFType.PACKET_IN, this);
		switchService.addOFSwitchListener(this);
	}
    
    //public abstract Command processPacketInMessage(IOFSwitch sw, OFPacketIn pi, 
	//		IRoutingDecision decision, FloodlightContext cntx);
 
    @Override
    public Command receive(IOFSwitch sw, OFMessage msg, FloodlightContext cntx) {
		// TODO Auto-generated method stub
		System.out.println("-------Command Receive in forwarding 2-----------");
		Ethernet eth =
				IFloodlightProviderService.bcStore.get(cntx,IFloodlightProviderService.CONTEXT_PI_PAYLOAD);
		
		switch (msg.getType()) {
		case PACKET_IN:
			 OFPacketIn myPacketIn = (OFPacketIn) msg;
	            OFPort myInPort = 
	                (myPacketIn.getVersion().compareTo(OFVersion.OF_12) < 0) 
	                ? myPacketIn.getInPort() 
	                : myPacketIn.getMatch().get(MatchField.IN_PORT);
	                
	                
	                if (eth.getEtherType() == EthType.IPv4) {
	                    /* We got an IPv4 packet; get the payload from Ethernet */
	                    IPv4 ipv4 = (IPv4) eth.getPayload();
	                     
	                    /* Various getters and setters are exposed in IPv4 */
	                    byte[] ipOptions = ipv4.getOptions();
	                    IPv4Address dstIp = ipv4.getDestinationAddress();
	                    IPv4Address srcIp = ipv4.getSourceAddress();
	                     System.out.println("Ipv4 src & dst: "+ srcIp+" , "+dstIp.toString());
	                     
	                     
	                     if (ipv4.getProtocol().equals(IpProtocol.TCP)) {
	                         /* We got a TCP packet; get the payload from IPv4 */
	                         TCP tcp = (TCP) ipv4.getPayload();
	                         log.info("TCP ");
	                         /* Various getters and setters are exposed in TCP */
	                         TransportPort srcPort = tcp.getSourcePort();
	                         TransportPort dstPort = tcp.getDestinationPort();
	                         short flags = tcp.getFlags();
	                          System.out.println("TCP Source and dst Port"+ srcPort.toString() + ", "+ dstPort.toString());
	                          //Your logic here! 
	                          //addFlow(sw, msg, eth, myPacketIn, srcPort, dstPort,srcIp, dstIp); 
	                          modFlow();
	                          
	                     } else if (ipv4.getProtocol().equals(IpProtocol.UDP)) {
	                         /* We got a UDP packet; get the payload from IPv4 */
	                         UDP udp = (UDP) ipv4.getPayload();
	                         log.info("UDP");
	                         // Various getters and setters are exposed in UDP 
	                         TransportPort srcPort = udp.getSourcePort();
	                         TransportPort dstPort = udp.getDestinationPort();
	                         System.out.println("UDP Source and dst Port"+ srcPort.toString() + ", "+ dstPort.toString());
	                         // Your logic here! 
	                         modFlow();
	                         //addFlow(sw, msg, eth, myPacketIn, srcPort, dstPort,srcIp, dstIp); 
	                     }
	                     
	                } else if (eth.getEtherType() == EthType.ARP) {
	                    /* We got an ARP packet; get the payload from Ethernet */
	                    ARP arp = (ARP) eth.getPayload();
	                    System.out.println("ARP: "+arp.toString());
	                    /* Various getters and setters are exposed in ARP */
	                    boolean gratuitous = arp.isGratuitous();
	         
	                } else {
	                    /* Unhandled ethertype */
	                    log.debug("UNhandled ethertype");
	                }
	                
	            //addFlow(sw, msg, eth, myPacketIn, srcPort, dstPort);    
	            break;
			
		default:
			break;
		}
		return Command.STOP;
		/* HOW to FORWARD PACKET IN TO default forwarding module*/
	}
    
    /*public void addFlow2(IOFSwitch sw, OFMessage msg,Ethernet eth,OFPacketIn pin){
    	
    	Ethernet l2 = new Ethernet();
    	l2.setSourceMACAddress(eth.getSourceMACAddress());
    	l2.setDestinationMACAddress(MacAddress.BROADCAST);
    	l2.setEtherType(EthType.IPv4);
    	
    	
    	IPv4 l3 = new IPv4();
    	l3.setSourceAddress(IPv4.of("192.168.1.1"));
    	l3.setDestinationAddress(IPv4.of("192.168.1.255"));
    	l3.setTtl((byte) 64);
    	l3.setProtocol(IpProtocol.UDP);
    	
    	UDP l4 = new UDP();
    	l4.setSourcePort(TransportPort.of(65003));
    	l4.setDestinationPort(TransportPort.of(67));
    	
    	Data l7 = new Data();
    	l7.setData(new byte[1000]);
    	
    	l2.setPayload(l3);
    	l3.setPayload(l4);
    	l4.setPayload(l7);
    	
    	byte[] serializedData = l2.serialize();
    	
    	OFPacketOut po = mySwitch.getOFFactory().buildPacketOut() // mySwitch is some IOFSwitch object 
    		    .setData(serializedData)
    		    .setActions(Collections.singletonList((OFAction) mySwitch.getOFFactory().actions().output(OFPort.FLOOD, 0xffFFffFF)))
    		    .setInPort(OFPort.CONTROLLER)
    		    .build();
    		  
    		mySwitch.write(po);
    	
    }*/
    
    public void modFlow(){
    	System.out.println("-------------------Mod FLow------------------------");
		IPv4Address h4Ip = IPv4Address.of("10.0.0.4"); //10.0.0.4
		IPv4Address h1Ip= IPv4Address.of("10.0.0.1"); //10.0.0.4
    	
		/*Switch 4*/
		
		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
		//ARP 1 S4 , dstIP=10.0.0.4, output=2
		IOFSwitch sw4 = switchService.getSwitch(DatapathId.of("00:00:00:00:00:00:00:04"));
		//System.out.println("Switch 4 inet address"+sw4.getInetAddress());
		OFFactory myFactoryS4 = sw4.getOFFactory();
		
		Match match = myFactoryS4.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.ARP)
				.setExact(MatchField.IPV4_DST,h4Ip )
				.build();
		
		U64 cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);
		OFActionOutput output = myFactoryS4.actions().buildOutput()
			    .setPort(OFPort.of(1))
			    .build();
		
		OFFlowAdd addFlow =  sw4.getOFFactory().buildFlowAdd().setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(5)
		.setMatch(match)
		.setActions(Collections.singletonList((OFAction) output))
		.build();
		
		sw4.write(addFlow);
		String entryarpS4= "flow1s4arp";
		aa.addFlow(entryarpS4, addFlow, DatapathId.of("00:00:00:00:00:00:00:04"));
		
		/*OFFlowMod.Builder fmb = sw4.getOFFactory().buildFlowAdd();
		Match.Builder mb = sw4.getOFFactory().buildMatch();
		fmb.setIdleTimeout(0);
        fmb.setHardTimeout(0);
        fmb.setPriority(100);
        
        mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
        fmb.setMatch(mb.build());*/
		
		//ARP 2 s4, dstIp=10.0.0.1, output=1
		Match matchArp2 = myFactoryS4.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.ARP)
				.setExact(MatchField.IPV4_DST,h1Ip )
				.build();
		
		OFActionOutput outputARP2 = myFactoryS4.actions().buildOutput()
			    .setPort(OFPort.of(2))
			    .build();
		
		OFFlowAdd addARP2 =  sw4.getOFFactory().buildFlowAdd().setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(5)
		.setMatch(matchArp2)
		.setActions(Collections.singletonList((OFAction) outputARP2))
		.build();
		
		sw4.write(addARP2);
		String entryarp2S4= "flow2s4arp";
		aa.addFlow(entryarp2S4, addARP2, DatapathId.of("00:00:00:00:00:00:00:04"));
		
		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
		//Flow 1 S4 , dstIP=10.0.0.4,output=2
		
		Match matchFlowS4 = myFactoryS4.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_DST,h4Ip )
				.setExact(MatchField.IPV4_SRC, h1Ip)
				.build();
		
		OFActionOutput outputs4 = myFactoryS4.actions().buildOutput()
			    .setPort(OFPort.of(1))
			    .build();
		
		OFFlowAdd addFlowS4 =  sw4.getOFFactory().buildFlowAdd().setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(5)
		.setMatch(matchFlowS4)
		.setActions(Collections.singletonList((OFAction) outputs4))
		.build();
		
		sw4.write(addFlowS4);
		String entryflow1S4= "flow1s4";
	    aa.addFlow(entryflow1S4, addFlowS4, DatapathId.of("00:00:00:00:00:00:00:04"));
	    
		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
		//Flow 2 S4 , dstIP=10.0.0.4, output=1
		
		Match matchFlow2S4 = myFactoryS4.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_DST,h1Ip )
				.setExact(MatchField.IPV4_SRC, h4Ip)
				.build();
		
		OFActionOutput output2s4 = myFactoryS4.actions().buildOutput()
			    .setPort(OFPort.of(2))
			    .build();
		
		OFFlowAdd addFlow2S4 =  sw4.getOFFactory().buildFlowAdd().setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(5)
		.setMatch(matchFlow2S4)
		.setActions(Collections.singletonList((OFAction) output2s4))
		.build();
		
		sw4.write(addFlow2S4);
		String entryflow2S4= "flow2s4";
		aa.addFlow(entryflow2S4, addFlow2S4, DatapathId.of("00:00:00:00:00:00:00:04"));
		
//-----------------------------------------------------------------------------------
		/*Switch 2*/
		
		IOFSwitch sw2 = switchService.getSwitch(DatapathId.of("00:00:00:00:00:00:00:02"));
		System.out.println("Switch 2 inet address"+sw2.getInetAddress());
		OFFactory myFactoryS2 = sw2.getOFFactory();
		
		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
		//ARP 1 S2 , dstIP=10.0.0.4, output=2
		
		Match matchARPS2 = myFactoryS2.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.ARP)
				.setExact(MatchField.IPV4_DST,h4Ip )
				.build();
		
		OFActionOutput outputARPS2 = myFactoryS2.actions().buildOutput()
			    .setPort(OFPort.of(2))
			    .build();
		
		OFFlowAdd addARPS2 =  sw2.getOFFactory().buildFlowAdd().setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(5)
		.setMatch(matchARPS2)
		.setActions(Collections.singletonList((OFAction) outputARPS2))
		.build();
		
		sw2.write(addARPS2);
   		String entryARPS2= "flow1s2arp";
		aa.addFlow(entryARPS2, addARPS2, DatapathId.of("00:00:00:00:00:00:00:02"));
		
		
		//ARP 2 s2, dstIp=10.0.0.1, output=3
		Match match2ArpS2 = myFactoryS2.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.ARP)
				.setExact(MatchField.IPV4_DST,h1Ip )
				.build();
		
		OFActionOutput output2ARPS2 = myFactoryS2.actions().buildOutput()
			    .setPort(OFPort.of(3))
			    .build();
		
		OFFlowAdd addARP2S2 =  sw2.getOFFactory().buildFlowAdd().setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(5)
		.setMatch(match2ArpS2)
		.setActions(Collections.singletonList((OFAction) output2ARPS2))
		.build();
		
		sw2.write(addARP2S2);
   		String entryARP2S2= "flow2s2arp";
		aa.addFlow(entryARP2S2, addARP2S2, DatapathId.of("00:00:00:00:00:00:00:02"));
		
		
		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
		//Flow 1 S2 , dstIP=10.0.0.4,output=2
		
		Match matchFlowS2 = myFactoryS2.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_DST,h4Ip )
				.setExact(MatchField.IPV4_SRC, h1Ip)
				.build();
		
		OFActionOutput outputS2 = myFactoryS2.actions().buildOutput()
			    .setPort(OFPort.of(2))
			    .build();
		
		OFFlowAdd addFlowS2 =  sw2.getOFFactory().buildFlowAdd().setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(5)
		.setMatch(matchFlowS2)
		.setActions(Collections.singletonList((OFAction) outputS2))
		.build();
		
		sw2.write(addFlowS2);
   		String entryFlowS2= "flow1s2";
		aa.addFlow(entryFlowS2, addFlowS2, DatapathId.of("00:00:00:00:00:00:00:02"));
		
		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
		//Flow 2 S2, dstIP=10.0.0.4, output=3
		
		Match matchFlow2S2 = myFactoryS2.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_DST,h1Ip )
				.setExact(MatchField.IPV4_SRC, h4Ip)
				.build();
		
		OFActionOutput output2s2 = myFactoryS2.actions().buildOutput()
			    .setPort(OFPort.of(3))
			    .build();
		
		OFFlowAdd addFlow2S2 =  sw2.getOFFactory().buildFlowAdd().setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(5)
		.setMatch(matchFlow2S2)
		.setActions(Collections.singletonList((OFAction) output2s2))
		.build();
		
		sw2.write(addFlow2S2);
   		String entryFlow2S2= "flow2s2";
		aa.addFlow(entryFlow2S2, addFlow2S2, DatapathId.of("00:00:00:00:00:00:00:02"));
		
	//-----------------------------------------------------------------------------------	
		/*Switch 1*/
		
		IOFSwitch sw1 = switchService.getSwitch(DatapathId.of("00:00:00:00:00:00:00:01"));
		System.out.println("Switch 1 inet address"+sw1.getInetAddress());
		OFFactory myFactoryS1 = sw1.getOFFactory();
		
		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
		//ARP 1 S1 , dstIP=10.0.0.4, output=3
		
		Match matchARPS1 = myFactoryS1.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.ARP)
				.setExact(MatchField.IPV4_DST,h4Ip )
				.build();
		
		OFActionOutput outputARPS1 = myFactoryS1.actions().buildOutput()
			    .setPort(OFPort.of(3))
			    .build();
		
		OFFlowAdd addARPS1 =  sw1.getOFFactory().buildFlowAdd().setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(5)
		.setMatch(matchARPS1)
		.setActions(Collections.singletonList((OFAction) outputARPS1))
		.build();
		
		sw1.write(addARPS1);
   		String entryARPS1= "flow1s1arp";
		aa.addFlow(entryARPS1, addARPS1, DatapathId.of("00:00:00:00:00:00:00:01"));
		
		//ARP 2 s1, dstIp=10.0.0.1, output=1
		Match match2ArpS1 = myFactoryS1.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.ARP)
				.setExact(MatchField.IPV4_DST,h1Ip )
				.build();
		
		OFActionOutput output2ARPS1 = myFactoryS1.actions().buildOutput()
			    .setPort(OFPort.of(1))
			    .build();
		
		OFFlowAdd addARP2S1 =  sw1.getOFFactory().buildFlowAdd().setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(5)
		.setMatch(match2ArpS1)
		.setActions(Collections.singletonList((OFAction) output2ARPS1))
		.build();
		
		sw1.write(addARP2S1);
   		String entryARP2S1= "flow2s1arp";
		aa.addFlow(entryARP2S1, addARP2S1, DatapathId.of("00:00:00:00:00:00:00:01"));
		
		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
		//Flow 1 S1 , dstIP=10.0.0.4,output=3
		
		Match matchFlowS1 = myFactoryS1.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_DST,h4Ip )
				.setExact(MatchField.IPV4_SRC, h1Ip)
				.build();
		
		OFActionOutput outputS1 = myFactoryS1.actions().buildOutput()
			    .setPort(OFPort.of(3))
			    .build();
		
		OFFlowAdd addFlowS1 =  sw1.getOFFactory().buildFlowAdd().setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(5)
		.setMatch(matchFlowS1)
		.setActions(Collections.singletonList((OFAction) outputS1))
		.build();
		
		sw1.write(addFlowS1);
   		String entryFlowS1= "flow1s1";
		aa.addFlow(entryFlowS1, addFlowS1, DatapathId.of("00:00:00:00:00:00:00:01"));
		
		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
		//Flow 2 S2, dstIP=10.0.0.4, output=1
		
		Match matchFlow2S1 = myFactoryS1.buildMatch()
				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
				.setExact(MatchField.IPV4_DST,h1Ip )
				.setExact(MatchField.IPV4_SRC, h4Ip)
				.build();
		
		OFActionOutput output2s1 = myFactoryS1.actions().buildOutput()
			    .setPort(OFPort.of(1))
			    .build();
		
		OFFlowAdd addFlow2S1 =  sw1.getOFFactory().buildFlowAdd().setHardTimeout(0)
		.setIdleTimeout(0)
		.setPriority(5)
		.setMatch(matchFlow2S1)
		.setActions(Collections.singletonList((OFAction) output2s1))
		.build();
		
		sw1.write(addFlow2S1);
   		String entryFlow2S1= "flow2s1";
		aa.addFlow(entryFlow2S1, addFlow2S1, DatapathId.of("00:00:00:00:00:00:00:01"));
		
    }
    
    
    
    public void addFlow(IOFSwitch sw, OFMessage msg,Ethernet eth,OFPacketIn pin,TransportPort srcPort, TransportPort dstPort,IPv4Address srcIp, IPv4Address dstIp){

    	log.info("Add Flow Function----------------------------------------------");
    	
    	//byte [] data = null;
    	//int offset =0;
    	//int length =0;
    	// get the correct builder for the OF version supported by the switch
    	//try {
    	
    		System.out.println("Detecting Switch ID: "+sw.getId().toString());
    		OFFactory myFactory = sw.getOFFactory(); /* Use the factory version appropriate for the switch in question. */
    		OFPacketOut.Builder pout = sw.getOFFactory().buildPacketOut();
    		
    		System.out.println("Buffered id: "+ pin.getBufferId());
    		System.out.println("source MAC: "+eth.getSourceMACAddress().toString());
    		System.out.println("Dest MAC: "+eth.getDestinationMACAddress().toString());
    		 System.out.println("Ipv4/ARP src & dst: "+ srcIp.toString()+" , "+dstIp.toString());
    		 System.out.println("TCP/UDP Source and dst Port"+ srcPort.toString() + ", "+ dstPort.toString());
    		System.out.println("Xid"+ pin.getXid());
    		//System.out.println("Xid"+ pin.);
    		/*
    		List<OFAction> actions = new ArrayList<OFAction>();
    		actions.add(sw.getOFFactory().actions().output(OFPort.of(1), Integer.MAX_VALUE));
    		pout.setActions(actions);
			*/
    		IPv4Address h4Ip = IPv4Address.of("10.0.0.4"); //10.0.0.4
    		IPv4Address h1Ip= IPv4Address.of("10.0.0.1"); //10.0.0.4
    		
    		//System.out.println("--------------------Inserting entries for S4------------------");
			//create ARP 1 
    		//there has ARP installed during initial setup
    		// Use packet in buffer if there is a buffer ID set 
    		/*boolean useBufferedPacket =true;
    		
    		if (useBufferedPacket) {
    			pout.setBufferId(pin.getBufferId()); // will be NO_BUFFER if there isn't one 
    		} else {
    			pout.setBufferId(OFBufferId.NO_BUFFER);
    		}

    		if (pout.getBufferId().equals(OFBufferId.NO_BUFFER)) {
    			//byte[] packetData = pin.getData();
    			//pout.setData(packetData);
    			pout.setXid(pin.getXid());
    			pout.setInPort(pin.getInPort());
    		}else{
    			log.info("in else ");
    		}
    		
    		//pout.setInPort((pin.getVersion().compareTo(OFVersion.OF_12) < 0 ? pin.getInPort() : pin.getMatch().get(MatchField.IN_PORT)));
    	
    		//sw.write(pout.build());
    		try {
    			//System.out.println("pout: "+pout.toString());
    			messageDamper.write(sw, pout.build());
    		} catch (IOException e) {
    			log.error("Failure writing packet out", e);
    		}*/
    		
    		/*
			Ethernet l2 = new Ethernet();
			l2.setEtherType(EthType.IPv4);
			l2.setSourceMACAddress(eth.getSourceMACAddress());
			l2.setDestinationMACAddress(eth.getDestinationMACAddress());
			
			
			IPv4 l3 = new IPv4();
			l3.setSourceAddress(srcIp);
			l3.setDestinationAddress(dstIp);
			l3.setProtocol(IpProtocol.TCP);
			
			TCP l4 = new TCP();
			l4.setSourcePort(srcPort);
			l4.setDestinationPort(dstPort);
			
			l2.setPayload(l3);
			l3.setPayload(l4);
			byte[] serialised = l2.serialize();
			
			OFActionOutput output = myFactory.actions().buildOutput()
				    .setPort(OFPort.of(1))
				    .build();

			OFPacketOut tcp = myFactory.buildPacketOut()
				    .setData(serialised)
				    .setXid(pin.getXid())
				    .setBufferId(pin.getBufferId())
				   .setActions(Collections.singletonList((OFAction) output))
				    .build();
		
			sw.write(tcp);
			*/
    		
    		/*Switch 4*/
    		
    		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
    		//ARP 1 S4 , dstIP=10.0.0.4, output=2
    		IOFSwitch sw4 = switchService.getSwitch(DatapathId.of("00:00:00:00:00:00:00:04"));
    		//System.out.println("Switch 4 inet address"+sw4.getInetAddress());
    		OFFactory myFactoryS4 = sw4.getOFFactory();
    		
    		Match match = myFactoryS4.buildMatch()
    				.setExact(MatchField.ETH_TYPE, EthType.ARP)
    				.setExact(MatchField.IPV4_DST,h4Ip )
    				.build();
    		
    		U64 cookie = AppCookie.makeCookie(FORWARDING_APP_ID, 0);
    		OFActionOutput output = myFactoryS4.actions().buildOutput()
				    .setPort(OFPort.of(3))
				    .build();
    		
    		OFFlowAdd addFlow =  sw4.getOFFactory().buildFlowAdd().setHardTimeout(0)
    		.setIdleTimeout(0)
    		.setPriority(50)
    		.setMatch(match)
    		.setCookie(cookie)
    		.setBufferId(OFBufferId.NO_BUFFER)
    		.setActions(Collections.singletonList((OFAction) output))
    		.build();
    		
    		sw4.write(addFlow);
    		//String entryarpS4= "newARP";
    		//aa.addFlow(entryarpS4, addFlow, DatapathId.of("00:00:00:00:00:00:00:04"));
    		
    		/*OFFlowMod.Builder fmb = sw4.getOFFactory().buildFlowAdd();
    		Match.Builder mb = sw4.getOFFactory().buildMatch();
    		fmb.setIdleTimeout(0);
            fmb.setHardTimeout(0);
            fmb.setPriority(100);
            
            mb.setExact(MatchField.ETH_TYPE, EthType.ARP);
            fmb.setMatch(mb.build());*/
    		
    		//ARP 2 s4, dstIp=10.0.0.1, output=1
    		Match matchArp2 = myFactoryS4.buildMatch()
    				.setExact(MatchField.ETH_TYPE, EthType.ARP)
    				.setExact(MatchField.IPV4_DST,h1Ip )
    				.build();
    		
    		OFActionOutput outputARP2 = myFactoryS4.actions().buildOutput()
				    .setPort(OFPort.of(1))
				    .build();
    		
    		OFFlowAdd addARP2 =  sw4.getOFFactory().buildFlowAdd().setHardTimeout(0)
    		.setIdleTimeout(0)
    		.setPriority(50)
    		.setMatch(matchArp2)
    		.setCookie(cookie)
    		.setBufferId(OFBufferId.NO_BUFFER)
    		.setActions(Collections.singletonList((OFAction) outputARP2))
    		.build();
    		
    		sw4.write(addARP2);
    		//String entryarp2S4= "newARP2";
    		//aa.addFlow(entryarp2S4, addARP2, DatapathId.of("00:00:00:00:00:00:00:04"));
    		
    		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
    		//Flow 1 S4 , dstIP=10.0.0.4,output=2
    		
    		Match matchFlowS4 = myFactoryS4.buildMatch()
    				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
    				.setExact(MatchField.IPV4_DST,h4Ip )
    				.setExact(MatchField.IPV4_SRC, h1Ip)
    				.build();
    		
    		OFActionOutput outputs4 = myFactoryS4.actions().buildOutput()
				    .setPort(OFPort.of(3))
				    .build();
    		
    		OFFlowAdd addFlowS4 =  sw4.getOFFactory().buildFlowAdd().setHardTimeout(0)
    		.setIdleTimeout(0)
    		.setPriority(50)
    		.setMatch(matchFlowS4)
    		.setCookie(cookie)
    		.setBufferId(OFBufferId.NO_BUFFER)
    		.setActions(Collections.singletonList((OFAction) outputs4))
    		.build();
    		
    		sw4.write(addFlowS4);
    		//String entryflow1S4= "newflow";
    	    //aa.addFlow(entryflow1S4, addFlowS4, DatapathId.of("00:00:00:00:00:00:00:04"));
    	    
    		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
    		//Flow 2 S4 , dstIP=10.0.0.4, output=1
    		
    		Match matchFlow2S4 = myFactoryS4.buildMatch()
    				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
    				.setExact(MatchField.IPV4_DST,h1Ip )
    				.setExact(MatchField.IPV4_SRC, h4Ip)
    				.build();
    		
    		OFActionOutput output2s4 = myFactoryS4.actions().buildOutput()
				    .setPort(OFPort.of(1))
				    .build();
    		
    		OFFlowAdd addFlow2S4 =  sw4.getOFFactory().buildFlowAdd().setHardTimeout(0)
    		.setIdleTimeout(0)
    		.setPriority(50)
    		.setMatch(matchFlow2S4)
    		.setCookie(cookie)
    		.setBufferId(OFBufferId.NO_BUFFER)
    		.setActions(Collections.singletonList((OFAction) output2s4))
    		.build();
    		
    		sw4.write(addFlow2S4);
    		//String entryflow2S4= "newFlow2";
    		//aa.addFlow(entryflow2S4, addFlow2S4, DatapathId.of("00:00:00:00:00:00:00:04"));
    		
    //-----------------------------------------------------------------------------------
    		/*Switch 2*/
    		
    		IOFSwitch sw2 = switchService.getSwitch(DatapathId.of("00:00:00:00:00:00:00:02"));
    		System.out.println("Switch 2 inet address"+sw2.getInetAddress());
    		OFFactory myFactoryS2 = sw2.getOFFactory();
    		
    		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
    		//ARP 1 S2 , dstIP=10.0.0.4, output=2
    		
    		Match matchARPS2 = myFactoryS2.buildMatch()
    				.setExact(MatchField.ETH_TYPE, EthType.ARP)
    				.setExact(MatchField.IPV4_DST,h4Ip )
    				.build();
    		
    		OFActionOutput outputARPS2 = myFactoryS2.actions().buildOutput()
				    .setPort(OFPort.of(2))
				    .build();
    		
    		OFFlowAdd addARPS2 =  sw2.getOFFactory().buildFlowAdd().setHardTimeout(0)
    		.setIdleTimeout(0)
    		.setPriority(50)
    		.setMatch(matchARPS2)
    		.setCookie(cookie)
    		.setBufferId(OFBufferId.NO_BUFFER)
    		.setActions(Collections.singletonList((OFAction) outputARPS2))
    		.build();
    		
    		sw2.write(addARPS2);
       		//String entryARPS2= "newARPS2";
    		//aa.addFlow(entryARPS2, addARPS2, DatapathId.of("00:00:00:00:00:00:00:02"));
    		
    		
    		//ARP 2 s2, dstIp=10.0.0.1, output=3
    		Match match2ArpS2 = myFactoryS2.buildMatch()
    				.setExact(MatchField.ETH_TYPE, EthType.ARP)
    				.setExact(MatchField.IPV4_DST,h1Ip )
    				.build();
    		
    		OFActionOutput output2ARPS2 = myFactoryS2.actions().buildOutput()
				    .setPort(OFPort.of(3))
				    .build();
    		
    		OFFlowAdd addARP2S2 =  sw2.getOFFactory().buildFlowAdd().setHardTimeout(0)
    		.setIdleTimeout(0)
    		.setPriority(50)
    		.setMatch(match2ArpS2)
    		.setCookie(cookie)
    		.setBufferId(OFBufferId.NO_BUFFER)
    		.setActions(Collections.singletonList((OFAction) output2ARPS2))
    		.build();
    		
    		sw2.write(addARP2S2);
       		//String entryARP2S2= "newARP2S2";
    		//aa.addFlow(entryARP2S2, addARP2S2, DatapathId.of("00:00:00:00:00:00:00:02"));
    		
    		
    		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
    		//Flow 1 S2 , dstIP=10.0.0.4,output=2
    		
    		Match matchFlowS2 = myFactoryS2.buildMatch()
    				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
    				.setExact(MatchField.IPV4_DST,h4Ip )
    				.setExact(MatchField.IPV4_SRC, h1Ip)
    				.build();
    		
    		OFActionOutput outputS2 = myFactoryS2.actions().buildOutput()
				    .setPort(OFPort.of(2))
				    .build();
    		
    		OFFlowAdd addFlowS2 =  sw2.getOFFactory().buildFlowAdd().setHardTimeout(0)
    		.setIdleTimeout(0)
    		.setPriority(50)
    		.setMatch(matchFlowS2)
    		.setCookie(cookie)
    		.setBufferId(OFBufferId.NO_BUFFER)
    		.setActions(Collections.singletonList((OFAction) outputS2))
    		.build();
    		
    		sw2.write(addFlowS2);
       		//String entryFlowS2= "newFlowS2";
    		//aa.addFlow(entryFlowS2, addFlowS2, DatapathId.of("00:00:00:00:00:00:00:02"));
    		
    		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
    		//Flow 2 S2, dstIP=10.0.0.4, output=3
    		
    		Match matchFlow2S2 = myFactoryS2.buildMatch()
    				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
    				.setExact(MatchField.IPV4_DST,h1Ip )
    				.setExact(MatchField.IPV4_SRC, h4Ip)
    				.build();
    		
    		OFActionOutput output2s2 = myFactoryS2.actions().buildOutput()
				    .setPort(OFPort.of(3))
				    .build();
    		
    		OFFlowAdd addFlow2S2 =  sw2.getOFFactory().buildFlowAdd().setHardTimeout(0)
    		.setIdleTimeout(0)
    		.setPriority(50)
    		.setMatch(matchFlow2S2)
    		.setCookie(cookie)
    		.setBufferId(OFBufferId.NO_BUFFER)
    		.setActions(Collections.singletonList((OFAction) output2s2))
    		.build();
    		
    		sw2.write(addFlow2S2);
       		//String entryFlow2S2= "newFlow2S2";
    		//aa.addFlow(entryFlow2S2, addFlow2S2, DatapathId.of("00:00:00:00:00:00:00:02"));
    		
    	//-----------------------------------------------------------------------------------	
    		/*Switch 1*/
    		
    		IOFSwitch sw1 = switchService.getSwitch(DatapathId.of("00:00:00:00:00:00:00:01"));
    		System.out.println("Switch 1 inet address"+sw1.getInetAddress());
    		OFFactory myFactoryS1 = sw1.getOFFactory();
    		
    		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
    		//ARP 1 S1 , dstIP=10.0.0.4, output=3
    		
    		Match matchARPS1 = myFactoryS1.buildMatch()
    				.setExact(MatchField.ETH_TYPE, EthType.ARP)
    				.setExact(MatchField.IPV4_DST,h4Ip )
    				.build();
    		
    		OFActionOutput outputARPS1 = myFactoryS1.actions().buildOutput()
				    .setPort(OFPort.of(3))
				    .build();
    		
    		OFFlowAdd addARPS1 =  sw1.getOFFactory().buildFlowAdd().setHardTimeout(0)
    		.setIdleTimeout(0)
    		.setPriority(50)
    		.setMatch(matchARPS1)
    		.setCookie(cookie)
    		.setBufferId(OFBufferId.NO_BUFFER)
    		.setActions(Collections.singletonList((OFAction) outputARPS1))
    		.build();
    		
    		sw1.write(addARPS1);
       		//String entryARPS1= "newARPS1";
    		//aa.addFlow(entryARPS1, addARPS1, DatapathId.of("00:00:00:00:00:00:00:01"));
    		
    		//ARP 2 s1, dstIp=10.0.0.1, output=1
    		Match match2ArpS1 = myFactoryS1.buildMatch()
    				.setExact(MatchField.ETH_TYPE, EthType.ARP)
    				.setExact(MatchField.IPV4_DST,h1Ip )
    				.build();
    		
    		OFActionOutput output2ARPS1 = myFactoryS1.actions().buildOutput()
				    .setPort(OFPort.of(1))
				    .build();
    		
    		OFFlowAdd addARP2S1 =  sw1.getOFFactory().buildFlowAdd().setHardTimeout(0)
    		.setIdleTimeout(0)
    		.setPriority(50)
    		.setMatch(match2ArpS1)
    		.setCookie(cookie)
    		.setBufferId(OFBufferId.NO_BUFFER)
    		.setActions(Collections.singletonList((OFAction) output2ARPS1))
    		.build();
    		
    		sw1.write(addARP2S1);
       		//String entryARP2S1= "newARP2S1";
    		//aa.addFlow(entryARP2S1, addARP2S1, DatapathId.of("00:00:00:00:00:00:00:01"));
    		
    		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
    		//Flow 1 S1 , dstIP=10.0.0.4,output=3
    		
    		Match matchFlowS1 = myFactoryS1.buildMatch()
    				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
    				.setExact(MatchField.IPV4_DST,h4Ip )
    				.setExact(MatchField.IPV4_SRC, h1Ip)
    				.build();
    		
    		OFActionOutput outputS1 = myFactoryS1.actions().buildOutput()
				    .setPort(OFPort.of(3))
				    .build();
    		
    		OFFlowAdd addFlowS1 =  sw1.getOFFactory().buildFlowAdd().setHardTimeout(0)
    		.setIdleTimeout(0)
    		.setPriority(50)
    		.setMatch(matchFlowS1)
    		.setCookie(cookie)
    		.setBufferId(OFBufferId.NO_BUFFER)
    		.setActions(Collections.singletonList((OFAction) outputS1))
    		.build();
    		
    		sw1.write(addFlowS1);
       		//String entryFlowS1= "newFlowS1";
    		//aa.addFlow(entryFlowS1, addFlowS1, DatapathId.of("00:00:00:00:00:00:00:01"));
    		
    		//"ipv4_src":"10.0.0.4", "ipv4_dst":"10.0.0.1" from packet in
    		//Flow 2 S2, dstIP=10.0.0.4, output=1
    		
    		Match matchFlow2S1 = myFactoryS1.buildMatch()
    				.setExact(MatchField.ETH_TYPE, EthType.IPv4)
    				.setExact(MatchField.IPV4_DST,h1Ip )
    				.setExact(MatchField.IPV4_SRC, h4Ip)
    				.build();
    		
    		OFActionOutput output2s1 = myFactoryS1.actions().buildOutput()
				    .setPort(OFPort.of(1))
				    .build();
    		
    		OFFlowAdd addFlow2S1 =  sw1.getOFFactory().buildFlowAdd().setHardTimeout(0)
    		.setIdleTimeout(0)
    		.setPriority(50)
    		.setMatch(matchFlow2S1)
    		.setCookie(cookie)
    		.setBufferId(OFBufferId.NO_BUFFER)
    		.setActions(Collections.singletonList((OFAction) output2s1))
    		.build();
    		
    		sw1.write(addFlow2S1);
       		//String entryFlow2S1= "newFlow2S1";
    		//aa.addFlow(entryFlow2S1, addFlow2S1, DatapathId.of("00:00:00:00:00:00:00:01"));
    		
    	//--------------------------------------------------------------------------------	
    	/* if (sw.getId().toString()== "00:00:00:00:00:00:00:01"){
			   System.out.println("--------------------Inserting entries for S1------------------");
			   //create ARP 1 
			   Ethernet l2 = new Ethernet();
			   l2.setEtherType(EthType.ARP);
			
			   IPv4 l3 = new IPv4();
			   l3.setDestinationAddress("10.0.0.4");
			
			   l2.setPayload(l3);
			   byte[] serialisedARP = l2.serialize();
			
			
			   OFPacketOut arp1 = sw.getOFFactory().buildPacketOut()
			     .setData(serialisedARP)
			     .setActions(Collections.singletonList((OFAction) sw.getOFFactory().actions().output(OFPort.of(3), Integer.MAX_VALUE)))
			     .setInPort(OFPort.CONTROLLER)
			     .build();
			
			   sw.write(arp1);
			
			   //create ARP 2 
			   Ethernet l2a = new Ethernet();
			   l2a.setEtherType(EthType.ARP);
			
			   IPv4 l3a = new IPv4();
			   l3a.setDestinationAddress("10.0.0.1");
			
			   l2a.setPayload(l3a);
			   byte[] serialisedARP2 = l2a.serialize();
			
			
			   OFPacketOut arp2 = sw.getOFFactory().buildPacketOut()
			      .setData(serialisedARP2)
			      .setActions(Collections.singletonList((OFAction) sw.getOFFactory().actions().output(OFPort.of(1), Integer.MAX_VALUE)))
			      .setInPort(OFPort.CONTROLLER)
			      .build();
			
			   sw.write(arp2);
			
			   //create new flow entry
			   Ethernet l2b = new Ethernet();
			   l2b.setEtherType(EthType.IPv4);
			
			   IPv4 l3b = new IPv4();
			   l3b.setSourceAddress("10.0.0.1");
			   l3b.setDestinationAddress("10.0.0.4");
			
			   l2b.setPayload(l3b);
			   byte[] serialisedflow = l2b.serialize();
			
			
			   OFPacketOut flow1 = sw.getOFFactory().buildPacketOut()
			      .setData(serialisedflow)
			      .setActions(Collections.singletonList((OFAction) sw.getOFFactory().actions().output(OFPort.of(3), Integer.MAX_VALUE)))
			      .setInPort(OFPort.CONTROLLER)
			      .build();
			
			   sw.write(flow1);
			
			   //create new flow entry 2
			   Ethernet l2c = new Ethernet();
			   l2c.setEtherType(EthType.IPv4);
			
			   IPv4 l3c = new IPv4();
			   l3c.setSourceAddress("10.0.0.4");
			   l3c.setDestinationAddress("10.0.0.1");
			   
			   l2c.setPayload(l3c);
			   byte[] serialisedflow2 = l2c.serialize();
				
			   OFPacketOut flow2 = sw.getOFFactory().buildPacketOut()
			      .setData(serialisedflow2)
			      .setActions(Collections.singletonList((OFAction) sw.getOFFactory().actions().output(OFPort.of(1), Integer.MAX_VALUE)))
			      .setInPort(OFPort.CONTROLLER)
			      .build();
			
			   sw.write(flow2);

		    }*/
    }//end addFlow function

	@Override
	public void switchAdded(DatapathId switchId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchRemoved(DatapathId switchId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchActivated(DatapathId switchId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchPortChanged(DatapathId switchId, OFPortDesc port,
			PortChangeType type) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void switchChanged(DatapathId switchId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void addFlow(String name, OFFlowMod fm, DatapathId swDpid) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deleteFlow(String name) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deleteFlowsForSwitch(DatapathId dpid) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void deleteAllFlows() {
		// TODO Auto-generated method stub
		
	}

	@Override
	public Map<String, Map<String, OFFlowMod>> getFlows() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Map<String, OFFlowMod> getFlows(DatapathId dpid) {
		// TODO Auto-generated method stub
		return null;
	}

}