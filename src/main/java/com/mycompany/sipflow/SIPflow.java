/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.sipflow;

/**
 *+
 * @author palmerg
 */
import com.googlecode.lanterna.TextColor;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SIPflow {
    
    List<SipCall> sipCallList;
    final Object sipCallListMonitor = new Object();
    int numSipMsg;
    final Object numSipMsgMonitor = new Object();
    final Object callTableMonitor = new Object();
    SimpleDateFormat logDateFormat; 
    int newCalls;
    
    @SuppressWarnings({"Convert2Diamond", "ResultOfObjectAllocationIgnored"})
    public SIPflow() {      
        sipCallList = new ArrayList<SipCall>();
        logDateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSXXX");
        new GuiThread(this);
        newCalls=0;
        numSipMsg=0;
    }

    public static void main(String[] args) throws IOException, InterruptedException {		
        SIPflow SIPflowObj = new SIPflow();
        File file = new File(args[0]); 
        SIPflowObj.ReadInput(new FileReader(file));
    }
    
    void ReadInput(Reader inputReader) throws InterruptedException {
        String outMessage = "";		
        String outSrcIp = "";
        String outSrcPort = "";
        String outDstIp = "";
        String outDstPort = "";
        String lineRead = "";
        Pattern beginMsgPattern = Pattern.compile("^\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{6}.*\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}.*\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}");
        Pattern datePattern = Pattern.compile("(?<date>\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})(\\d{3})(?<tz>(-|\\+)\\d{2}:\\d{2})");
        Pattern srcIPPattern = Pattern.compile("(?<address>\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(.|:)(?<port>\\d*)(?= >)");
        Pattern dstIpPattern = Pattern.compile("(?<=> )(?<address>\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})(.|:)(?<port>\\d*)");
        BufferedReader inputBuffered = new BufferedReader(inputReader);
        Matcher beginMsgMatcher = beginMsgPattern.matcher(lineRead);
        Matcher dateMatcher = datePattern.matcher(lineRead);
        Matcher srcIpMatcher = srcIPPattern.matcher(lineRead);
        Matcher dstIpMatcher = dstIpPattern.matcher(lineRead);
        boolean foundNextMsg = false;

        try {
            Date outTimeDateStamp = logDateFormat.parse("2000-01-01 00:00:00.000-00:00");
            //loop trough all data
            while (foundNextMsg||((lineRead=inputBuffered.readLine()) != null))
            {
                foundNextMsg = false;
                //match on begining of message pattern
                beginMsgMatcher.reset(lineRead);
                if (beginMsgMatcher.find(0)) {
                    //add line to outMessage
                    outMessage += lineRead + "\r\n";   
                    //find time date stamp
                    dateMatcher.reset(lineRead);
                    if (dateMatcher.find()) {
                            outTimeDateStamp=logDateFormat.parse(dateMatcher.group("date")+dateMatcher.group("tz"));
                    }
                    //find source ip and port
                    srcIpMatcher.reset(lineRead);
                    if(srcIpMatcher.find() ){					
                            outSrcIp = srcIpMatcher.group("address");
                            outSrcPort = srcIpMatcher.group("port");
                    }
                    //find destination ip and port
                    dstIpMatcher.reset(lineRead);
                    if(dstIpMatcher.find()){
                            outDstIp = dstIpMatcher.group("address");
                            outDstPort = dstIpMatcher.group("port");
                    }
                    //loop and read from data
                    while ((lineRead=inputBuffered.readLine()) != null) {
                        //match if begining of line next message 
                        beginMsgMatcher.reset(lineRead);
                        if (beginMsgMatcher.find()) {				
                            //create SipMessage and parse it to SipCalls to be added to a sipcall in the sipCallList  
                            ParseSipCalls(new SipMessage(outMessage,
                                            outTimeDateStamp,
                                            outSrcIp,
                                            outSrcPort,
                                            outDstIp,
                                            outDstPort));

                            //clear the variables after they have been added to a sipMessage
                            outMessage = "";						
                            outSrcIp = "";
                            outSrcPort = "";
                            outDstIp = "";
                            outDstPort = "";					

                            //add the begining line of the next message to the outMessage
                            //outMessage += lineRead + "\r\n";
                            //set foundNextMsg to prevent while reading the next line
                            foundNextMsg = true;
                            break;
                        }
                        outMessage += lineRead + "\r\n";
                    }
                }
            }
        } catch (IOException | ParseException e) {
            // TODO Auto-generated catch block

        }
    }
        
    private void ParseSipCalls(SipMessage inputSipMessage) throws IOException, InterruptedException {
         if(inputSipMessage.getCallID() != null){   
             synchronized(numSipMsgMonitor) {numSipMsg++;}
             boolean callIdWasFound = false;
            //find sipcall in sipcall list 
            synchronized(sipCallListMonitor){
                for(int i=0;i <sipCallList.size();i++) {			
                    if(inputSipMessage.getCallID().equals(sipCallList.get(i).getCallId())) {
                        callIdWasFound = true;
                        sipCallList.get(i).addSipMessage(inputSipMessage);                                
                        break;
                    }
                }
            }
            
            if (!callIdWasFound ) {
                synchronized(sipCallListMonitor){sipCallList.add(new SipCall(inputSipMessage.getCallID(),inputSipMessage));}               
                //increment the number os new calls for the GUI to add to the table
                synchronized(callTableMonitor){newCalls++;}
            }
        }
    }
        
    static final class SipCall {
        
        private final String callId;
	private Date startTime;
	private Date endTime;
	private String toAorUser;
	private String toName;
	private String fromAorUser;
	private String fromName;
	private String uacIp;
	private String uacPort;
	private String uasIp;
	private String uasPort;
	private String method;
	private String uacSdpIp;
	private String uacSdpPort;
	private String uacSdpCodec;
	private String uasSdpIp;
	private String uasSdpPort;
	private String uasSdpCodec;
	private String lastCseq;
	List<SipMessage> sipMessages;
	
	SipCall(String inputCallId,SipMessage inputSipMessage){
            
            sipMessages = new ArrayList();
            callId = inputCallId;
            toAorUser = "";
            toName = "";
            fromAorUser = "";
            fromName = "";
            uacIp = "";
            uacPort = "";
            uasIp = "";
            uasPort = "";
            method = "";
            uacSdpIp = "";
            uacSdpPort = "";
            uacSdpCodec = "";
            uasSdpIp = "";
            uasSdpPort = "";
            uasSdpCodec = "";
            lastCseq = "";
            addSipMessage(inputSipMessage);
	}
	
	void addSipMessage(SipMessage inputSipMessage){
            sipMessages.add(inputSipMessage);
            if ((startTime==null)||(inputSipMessage.getTimeDateStamp().compareTo(startTime) < 0)) startTime = inputSipMessage.getTimeDateStamp();
            if (inputSipMessage.getTimeDateStamp().compareTo(startTime) > 0) endTime = inputSipMessage.getTimeDateStamp();
            if (toAorUser=="") toAorUser = inputSipMessage.getToAorUser();
            if (toName=="") toName = inputSipMessage.getToName();
            if (fromAorUser=="") fromAorUser = inputSipMessage.getFromAorUser();
            if (fromName=="") fromName = inputSipMessage.getFromName();
            if (uacIp=="") {
                    uacIp = inputSipMessage.getSrcIp();
                    uacPort = inputSipMessage.getSrcPort();
            }		
            if (!inputSipMessage.getDstIp().equals(uacIp) ) {
                    uasIp = inputSipMessage.getDstIp();
                    uasPort = inputSipMessage.getDstPort();
            }
            if (method=="") method = inputSipMessage.getMethod();
            if (
                    (inputSipMessage.hasSdp())&&
                    (inputSipMessage.getSrcIp().isEmpty())&&
                    (inputSipMessage.getSrcPort().isEmpty())
                    ) {
                            uacSdpIp = inputSipMessage.getSdpIp();
                            uacSdpPort = inputSipMessage.getSdpPort();
                            uacSdpCodec = inputSipMessage.getSdpCodec();
            }
            if (
                    (inputSipMessage.hasSdp())&&
                    (inputSipMessage.getSrcIp().isEmpty())&&
                    (inputSipMessage.getSrcPort().isEmpty())
                    ) {
                            uasSdpIp = inputSipMessage.getSdpIp();
                            uasSdpPort = inputSipMessage.getSdpPort();
                            uasSdpCodec = inputSipMessage.getSdpCodec();
            }
            lastCseq = inputSipMessage.getcSeq(); 
	}

	public String getCallId() {
		return callId;
	}

	public Date getStartTime() {
		return startTime;
	}

	public Date getEndTime() {
		return endTime;
	}

	public String getToAorUser() {
		return toAorUser;
	}

	public String getToName() {
		return toName;
	}

	public String getFromAorUser() {
		return fromAorUser;
	}

	public String getFromName() {
		return fromName;
	}

	public String getUacIp() {
		return uacIp;
	}

	public String getUacPort() {
		return uacPort;
	}

	public String getUasIp() {
		return uasIp;
	}

	public String getUasPort() {
		return uasPort;
	}

	public String getMethod() {
		return method;
	}

	public String getUacSdpIp() {
		return uacSdpIp;
	}

	public String getUacSdpPort() {
		return uacSdpPort;
	}

	public String getUacSdpCodec() {
		return uacSdpCodec;
	}

	public String getUasSdpIp() {
		return uasSdpIp;
	}

	public String getUasSdpPort() {
		return uasSdpPort;
	}

	public String getUasSdpCodec() {
		return uasSdpCodec;
	}

	public String getLastCseq() {
		return lastCseq;
	}

	public List<SipMessage> getSipMessages() {
		return sipMessages;
	}

        @Override
        public String toString() {
            return "SipCall{" + "callId=" + callId + ", startTime=" + startTime 
                    + ", endTime=" + endTime + ", toAorUser=" + toAorUser 
                    + ", toName=" + toName + ", fromAorUser=" + fromAorUser 
                    + ", fromName=" + fromName + ", uacIp=" + uacIp 
                    + ", uacPort=" + uacPort + ", uasIp=" + uasIp 
                    + ", uasPort=" + uasPort + ", method=" + method 
                    + ", uacSdpIp=" + uacSdpIp + ", uacSdpPort=" + uacSdpPort 
                    + ", uacSdpCodec=" + uacSdpCodec + ", uasSdpIp=" + uasSdpIp 
                    + ", uasSdpPort=" + uasSdpPort + ", uasSdpCodec=" 
                    + uasSdpCodec + ", lastCseq=" + lastCseq + '}';
        }
    }

    static class SipMessage {

        private final Date timeDateStamp;	
	private final String srcIp;
	private final String srcPort;
	private final String dstIp;
	private final String dstPort;
	private String req;
	private String method;
	private String response;
	private String callID;
	private String toName;
	private String toAorUser;
	private String fromName;
	private String fromAorUser;
	private TextColor color;
	private boolean hasSdp;
	private String sdpIp;
	private String sdpPort;
	private String sdpCodec;
	private String ua;
	private String cSeq;
	private final String message;
        
	SipMessage(
                    String inputMessage,
                    Date inputTimeDateStamp,
                    String inputSrcIp,
                    String inputSrcPort,
                    String inputDstIp,
                    String inputDstPort
                    ){
            message = inputMessage;
            timeDateStamp = inputTimeDateStamp;
            srcIp = inputSrcIp;
            srcPort = inputSrcPort;
            dstIp = inputDstIp;
            dstPort = inputDstPort;

            Pattern reqPattern = Pattern.compile("((?<method>(ACK|BYE|CANCEL|INFO|INVITE|MESSGAGE|NOTIFY|OPTIONS|PRACK|PUBLISH|REFER|REGISTER|SUBSCRIBE|UPDATE))\\s+(?<uri>.*)\\s+(SIP\\/2.0)$)|SIP\\/2.0 (?<response>\\d{3}.*)",Pattern.MULTILINE);
            Pattern callIDPattern = Pattern.compile("(?<!-.{8})(?<=Call-ID:)\\s*(?<callid>\\S*)",Pattern.MULTILINE);
            Pattern toPattern = Pattern.compile("To:\\s*(?<name>\\x22.+\\x22)?\\d*\\s*<?(sip:)(?<aorUser>[^@>]+)",Pattern.MULTILINE);
            Pattern fromPattern = Pattern.compile("From:\\s*(?<name>\\x22.+\\x22)?\\d*\\s*<?(sip:)(?<aorUser>[^@>]+)",Pattern.MULTILINE);
            Pattern hasSdpPattern = Pattern.compile("Content-Type: application\\/sdp",Pattern.MULTILINE);
            Pattern sdpIpPattern = Pattern.compile("(?<=c=IN IP4 )\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}",Pattern.MULTILINE);
            Pattern sdpPortPattern = Pattern.compile("(?<=m=audio )\\d*",Pattern.MULTILINE);
            Pattern sdpCodecPattern = Pattern.compile("(?<=RTP\\/AVP )\\d*",Pattern.MULTILINE);
            Pattern uaPattern = Pattern.compile("(?<=User-Agent:)\\s*(?<ua>.*)",Pattern.MULTILINE);
            Pattern serverPattern = Pattern.compile("(?<=Server:)\\s*(?<server>.*)",Pattern.MULTILINE);
            Pattern cSeqPattern = Pattern.compile("CSeq:\\s?(?<cseq>(\\d{1,3})\\s?(\\w*))",Pattern.MULTILINE);		

            Matcher reqMatcher = reqPattern.matcher(message);
            Matcher callIDMatcher = callIDPattern.matcher(message);
            Matcher toMatcher = toPattern.matcher(message);
            Matcher fromMatcher = fromPattern.matcher(message);
            Matcher hasSdpMatcher = hasSdpPattern.matcher(message);
            Matcher sdpIpMatcher = sdpIpPattern.matcher(message);
            Matcher sdpPortMatcher = sdpPortPattern.matcher(message);
            Matcher sdpCodecMatcher = sdpCodecPattern.matcher(message);
            Matcher uaMatcher = uaPattern.matcher(message);
            Matcher severMatcher = serverPattern.matcher(message);
            Matcher cSeqMatcher = cSeqPattern.matcher(message);		

            if(reqMatcher.find()) {
                    method = reqMatcher.group("method");
                    response = reqMatcher.group("response");
                    req = (method == null ? "" : method) + reqMatcher.group("uri") + (response == null ? "" : response) ;			
            }
            if(callIDMatcher.find()) {
                    callID = callIDMatcher.group("callid");
            }
            if(toMatcher.find()) {
                    toName = toMatcher.group("name");
                    toAorUser = toMatcher.group("aorUser");
            }
            if(fromMatcher.find()) {
                    fromName = fromMatcher.group("name");
                    fromAorUser = fromMatcher.group("aorUser");
            }
            if(hasSdpMatcher.find()) {
                    hasSdp = true;
                    if(sdpIpMatcher.find()) {
                            sdpIp = sdpIpMatcher.group(0);
                    }
                    if(sdpPortMatcher.find()) {
                            sdpPort = sdpPortMatcher.group(0);
                    }
                    if(sdpCodecMatcher.find()) {
                            sdpCodec = sdpCodecMatcher.group(0);
                    }
            }
            if(uaMatcher.find()) {
                    ua = uaMatcher.group("ua");						
            }
            else{
                    if(severMatcher.find()) {
                            ua = severMatcher.group("server");
                    }
            }
            if(cSeqMatcher.find()) {
                    cSeq = cSeqMatcher.group("cseq");
            }
	}

	public Date getTimeDateStamp() {
		return timeDateStamp;
	}
	
	public String getSrcIp() {
		return srcIp;
	}
	
	public String getSrcPort() {
		return srcPort;
	}
	
	public String getDstIp() {
		return dstIp;
	}
	
	public String getDstPort() {
		return dstPort;
	}
	
	public TextColor getColor() {
		return color;
	}

	public void setColor(TextColor color) {
		this.color = color;
	}

	public String getReq() {
		return req;
	}

	public String getMethod() {
		return method;
	}

	public String getResponse() {
		return response;
	}

	public String getCallID() {
		return callID;
	}

	public String getToName() {
		return toName;
	}

	public String getToAorUser() {
		return toAorUser;
	}

	public String getFromName() {
		return fromName;
	}

	public String getFromAorUser() {
		return fromAorUser;
	}

	public boolean hasSdp() {
		return hasSdp;
	}

	public String getSdpIp() {
		return sdpIp;
	}

	public String getSdpPort() {
		return sdpPort;
	}

	public String getSdpCodec() {
		return sdpCodec;
	}

	public String getUa() {
		return ua;
	}

	public String getcSeq() {
		return cSeq;
	}

	public String getMessage() {
		return message;
	}

	@Override
	public String toString() {
		return "sipMessage [timeDateStamp=" + timeDateStamp + ", srcIp=" 
                        + srcIp + ", srcPort=" + srcPort + ", dstIp="+ dstIp 
                        + ", dstPort=" + dstPort + ", req=" + req + ", method=" 
                        + method + ", response=" + response + ", callID=" 
                        + callID + ", toName=" + toName + ", toAorUser=" 
                        + toAorUser + ", fromName=" + fromName 
                        + ", fromAorUser=" + fromAorUser + ", color=" + color 
                        + ", hasSdp=" + hasSdp + ", sdpIp=" + sdpIp 
                        + ", sdpPort=" + sdpPort + ", sdpCodec=" + sdpCodec 
                        + ", ua=" + ua + ", cSeq=" + cSeq + ", message="
                        + message + "]";
	}
    }

    private static class ReaderThread implements Runnable {

        Reader reader;
        SIPflow sipflow;
        public ReaderThread(SIPflow inputSIPflow, Reader inputReader)  {
            reader = inputReader;
            sipflow = inputSIPflow;
            new Thread(this, "ReaderThread").start();
        }

        @Override
        public void run() {
            try {
                sipflow.ReadInput(reader); //To change body of generated methods, choose Tools | Templates.
            } catch (InterruptedException ex) {
                Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }

    private static class GuiThread implements Runnable{
        SIPflow obj;
        public GuiThread(SIPflow inputSIPflowObj) {
            obj = inputSIPflowObj;
            new Thread(this, "GuiThread").start();
        }
        
        @Override
        public void run() {
            try {
                new Gui(obj);
            } catch (InterruptedException | IOException ex) {
                Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}



	



