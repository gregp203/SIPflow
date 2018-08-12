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

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBoxList;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextGUIThread;
import com.googlecode.lanterna.gui2.TextGUI;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SIPflow {
	
    
    List<SipCall> sipCallList;
    List<String> selectedCallIds;
    private final Object sipCallListMonitor = new Object();
    int numSipMsg;
    private final Object numSipMsgMonitor = new Object();
    WindowBasedTextGUI textGUI;
    BasicWindow windowA;
    Panel mainPanel;
    Panel callsPanel;
    Panel buttonPanel;
    Panel methodPannel;
    Panel inputStatusPannel;
    Table<String> table;
    Terminal term;
    Screen screen;
    Label inputStatusLabel;
    
    private final Object callTableMonitor = new Object();
    DateFormat tableStartDateFormat;
    DateFormat tableEndDateFormat;
    SipCallDisplay callFilter;
    List<String> methodFilter;
    CheckBoxList<String> methodCheckBox;
    int newCalls;

    public SIPflow() {      
        sipCallList = new ArrayList<SipCall>();
        callFilter = new SipCallDisplay();
        selectedCallIds = new ArrayList<String>();
        tableStartDateFormat = new SimpleDateFormat("yyyy-MM-dd'@'HH:mm:ss.SSSZ");
        tableEndDateFormat = new SimpleDateFormat("yyyy-MM-dd'@'HH:mm:ss");
        methodFilter = new ArrayList<String>();
        //methodFilter.add("INVITE");
        new GuiThread(this);
        newCalls=0;
        numSipMsg=0;
    }

    public static void main(String[] args) throws IOException, InterruptedException {		
        SIPflow SIPflowObj = new SIPflow();
        File file = new File(args[0]); 
        SIPflowObj.ReadInput(new FileReader(file));
    }
	
    void Gui() throws InterruptedException, IOException{

        try {
            
            term = new DefaultTerminalFactory().createTerminal();
            screen = new TerminalScreen(term);
            textGUI = new MultiWindowTextGUI(screen);
            screen.startScreen();
            
            windowA = new BasicWindow();
            
            mainPanel = new Panel();
            callsPanel = new Panel();
            buttonPanel = new Panel();
            methodPannel= new Panel();
            inputStatusPannel = new Panel();
            
            inputStatusLabel = new Label("");
            table = new Table<String>(
                    "*",
                    "Start Time", 
                    "End Time",
                    "Method",
                    "From",
                    "To",
                    "UAC IP",
                    "UAS IP",
                    "Last CSeq",
                    "Call-ID"
            ); 
            table.setSelectAction(new Runnable() {
		@Override
		public void run() {
                    if(table.getTableModel().getCell(0, table.getSelectedRow()).equals("[x]")){
                        table.getTableModel().setCell(0, table.getSelectedRow(), "[ ]");
                        selectedCallIds.remove(table.getTableModel().getCell(9, table.getSelectedRow()));
                    }
                    else{
                        table.getTableModel().setCell(0, table.getSelectedRow(), "[x]");
                        selectedCallIds.add(table.getTableModel().getCell(9, table.getSelectedRow()));
                    }
		}		
            });
            
            TerminalSize methodCheckBoxsize = new TerminalSize(16, 5);
            methodCheckBox= new CheckBoxList<String>(methodCheckBoxsize);
            methodCheckBox.addItem("INVITE");
            methodCheckBox.addItem("NOTIFY");
            methodCheckBox.addItem("OPTIONS");
            methodCheckBox.addItem("REGISTER");
            methodCheckBox.addItem("SUBSCRIBE");
            methodCheckBox.setChecked("INVITE", true);
            methodFilter = methodCheckBox.getCheckedItems();
            
            Button methodApplylButton = new Button("Apply", new Runnable() {
		@Override
		public void run() {
			refreshCallTable();
		}
            });
            
            Button startButton = new Button("Start", new Runnable() {
		@Override
		public void run() {
			// Actions go here
		}
            });
            
            Button stopButton = new Button("Stop", new Runnable() {
		@Override
		public void run() {
			// Actions go here
		}
            });
            
            Button filterButton = new Button("Filter", new Runnable() {
		@Override
		public void run() {
			// Actions go here
		}
            });
            
            Button flowButton = new Button("Flow", new Runnable() {
		@Override
		public void run() {
			// Actions go here
		}
            });
            
            
            
            mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
            
            methodPannel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
            methodPannel.addComponent(methodCheckBox);
            methodPannel.addComponent(methodApplylButton);
                        
            buttonPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL));
            buttonPanel.addComponent(methodPannel.withBorder(Borders.singleLine("Methods")));
            buttonPanel.addComponent(startButton);
            buttonPanel.addComponent(stopButton);
            buttonPanel.addComponent(filterButton);            
            buttonPanel.addComponent(flowButton);
            
            
            inputStatusPannel.addComponent(inputStatusLabel);           
            
            callsPanel.addComponent(table);
            
            mainPanel.addComponent(buttonPanel.withBorder(Borders.singleLine("Buttons")));
            mainPanel.addComponent(inputStatusPannel.withBorder(Borders.singleLine("Input")));
            mainPanel.addComponent(callsPanel.withBorder(Borders.singleLine("Calls")));                    
            
            windowA.setComponent(mainPanel);            
            
            textGUI.addWindow(windowA);
            waitForTextGUI(windowA);
            
        } catch (IOException ex) {
            Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
    
    void waitForTextGUI(BasicWindow window) {
        while(window.getTextGUI() != null) {
            boolean sleep = true;
            TextGUIThread guiThread = textGUI.getGUIThread();
            
                try {
                    sleep = !guiThread.processEventsAndUpdate();
                }
                catch(EOFException ignore) {
                    //The GUI has closed so allow exit
                    break;
                }
                catch(IOException e) {
                    throw new RuntimeException("Unexpected IOException while waiting for window to close", e);
                }
            
            if(sleep) {
                try {
                    Thread.sleep(1);
                    synchronized(callTableMonitor){
                        for(;newCalls >0;newCalls--){
                            int i = sipCallList.size() - newCalls;
                            UpdateCallTable(sipCallList.get(i),callFilter);
                        }
                    }
                    updateInputStauts();
                }
                catch(InterruptedException ignore) {}
            }
        }
    }
    
    void updateInputStauts(){        
        int numberOfMsg;
        int numberOfCallIds;
        synchronized(numSipMsgMonitor) {numberOfMsg = numSipMsg;}
        synchronized(sipCallListMonitor){numberOfCallIds = sipCallList.size();}
        inputStatusLabel.setText("Number of SIP messages found : "+ numberOfMsg +" Number of CallIDs found : "+ numberOfCallIds);
    }
    
    void refreshCallTable(){
        selectedCallIds.clear();
        methodFilter.clear();
        synchronized(callTableMonitor){
            int tableLength = table.getSize().getRows();
            for (int i =tableLength; i>1; i--) {
                table.getTableModel().removeRow(0);
            } 
        }
        methodFilter = methodCheckBox.getCheckedItems();
        for(int i= 0; i<sipCallList.size();i++){
            UpdateCallTable(sipCallList.get(i),callFilter);
        }
    }
    

    void ReadInput(Reader inputReader) throws InterruptedException {
        //wait for GUI thread to be ready
        
        
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSXXX");
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
            Date outTimeDateStamp = dateFormat.parse("2000-01-01 00:00:00.000-00:00");
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
                            outTimeDateStamp=dateFormat.parse(dateMatcher.group("date")+dateMatcher.group("tz"));
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
                            //get the last SipMessage in SIPmesagesList and parse it to SipCalls  
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
                e.printStackTrace();
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
    
    private void UpdateCallTable(SipCall inputCall,SipCallDisplay filter){
            boolean callMatches = false;
            if(methodFilter.contains(inputCall.getMethod())){
                if(filter.IsAllNull()) callMatches = true;
                if(!callMatches && inputCall.getCallId().contains(filter.getCallId())) callMatches = true;
                if(!callMatches && inputCall.getStartTime().after(filter.getStartTime())) callMatches = true;
                if(!callMatches && inputCall.getEndTime().before(filter.getEndTime())) callMatches = true;
                if(!callMatches && inputCall.getToAorUser().contains(filter.getToAorUser())) callMatches = true;
                if(!callMatches && inputCall.getToName().contains(filter.getToName())) callMatches = true;
                if(!callMatches && inputCall.getFromAorUser().contains(filter.getFromAorUser())) callMatches = true;
                if(!callMatches && inputCall.getFromName().contains(filter.getFromName())) callMatches = true;
                if(!callMatches && inputCall.getUacIp().contains(filter.getUacIp())) callMatches = true;
                if(!callMatches && inputCall.getUacPort().contains(filter.getUacPort())) callMatches = true;
                if(!callMatches && inputCall.getUasIp().contains(filter.getUasIp())) callMatches = true;
                if(!callMatches && inputCall.getUasPort().contains(filter.getUasPort())) callMatches = true;                
                if(!callMatches && inputCall.getUacSdpIp().contains(filter.getUacSdpIp())) callMatches = true;
                if(!callMatches && inputCall.getUacSdpPort().contains(filter.getUacSdpPort())) callMatches = true;
                if(!callMatches && inputCall.getUacSdpCodec().contains(filter.getUacSdpCodec())) callMatches = true;
                if(!callMatches && inputCall.getUasSdpIp().contains(filter.getUasSdpIp())) callMatches = true;
                if(!callMatches && inputCall.getUasSdpPort().contains(filter.getUasSdpPort())) callMatches = true;
                if(!callMatches && inputCall.getUasSdpCodec().contains(filter.getUasSdpCodec())) callMatches = true;
                if(!callMatches && inputCall.getLastCseq().contains(filter.getLastCseq())) callMatches = true;
            }
            if(callMatches){
                
                {    
                    table.getTableModel().addRow(
                            "[ ]",
                            tableStartDateFormat.format(inputCall.getStartTime()),
                            tableEndDateFormat.format(inputCall.getEndTime()),
                            inputCall.getMethod(),
                            inputCall.getFromAorUser(),
                            inputCall.getToAorUser(),
                            inputCall.getUacIp(),
                            inputCall.getUasIp(),
                            inputCall.getLastCseq(),
                            inputCall.getCallId()
                    );
                }
            }
    }
        
    
        

    private static class SipCall {
        
        private String callId;
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
            if ((endTime==null)||(inputSipMessage.getTimeDateStamp().compareTo(endTime) > 0)) endTime = inputSipMessage.getTimeDateStamp();
            if (toAorUser == "") toAorUser = inputSipMessage.getToAorUser();
            if (toName == "") toName = inputSipMessage.getToName();
            if (fromAorUser == "") fromAorUser = inputSipMessage.getFromAorUser();
            if (fromName == "") fromName = inputSipMessage.getFromName();
            if (uacIp == "") {
                    uacIp = inputSipMessage.getSrcIp();
                    uacPort = inputSipMessage.getSrcPort();
            }		
            if (!inputSipMessage.getDstIp().equals(uacIp) ) {
                    uasIp = inputSipMessage.getDstIp();
                    uasPort = inputSipMessage.getDstPort();
            }
            if (method == "") method = inputSipMessage.getMethod();
            if (
                    (inputSipMessage.hasSdp())&&
                    (inputSipMessage.getSrcIp() == uacIp)&&
                    (inputSipMessage.getSrcPort() == uacPort)
                    ) {
                            uacSdpIp = inputSipMessage.getSdpIp();
                            uacSdpPort = inputSipMessage.getSdpPort();
                            uacSdpCodec = inputSipMessage.getSdpCodec();
            }
            if (
                    (inputSipMessage.hasSdp())&&
                    (inputSipMessage.getSrcIp() == uasIp)&&
                    (inputSipMessage.getSrcPort() == uasPort)
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

    private static class SipMessage {

        private Date timeDateStamp;	
	private String srcIp;
	private String srcPort;
	private String dstIp;
	private String dstPort;
	private String req;
	private String method;
	private String response;
	private String callID;
	private String toName;
	private String toAorUser;
	private String fromName;
	private String fromAorUser;
	private String color;
	private boolean hasSdp;
	private String sdpIp;
	private String sdpPort;
	private String sdpCodec;
	private String ua;
	private String cSeq;
	private String message;
        

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
            Pattern cSeqPattern = Pattern.compile("CSeq:\\s?(\\d{1,3})\\s?(\\w*)",Pattern.MULTILINE);		

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
                    req = method+reqMatcher.group("uri")+response;			
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
                    cSeq = cSeqMatcher.group(0);
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
	
	public String getColor() {
		return color;
	}

	public void setColor(String color) {
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
    
    private static class SipCallDisplay {
        
        private boolean selected;
        private String callId;
	private Date startTime;
	private Date endTime;
        private String method;
	private String toAorUser;
	private String toName;
	private String fromAorUser;
	private String fromName;
	private String uacIp;
	private String uacPort;
	private String uasIp;
	private String uasPort;	
	private String uacSdpIp;
	private String uacSdpPort;
	private String uacSdpCodec;
	private String uasSdpIp;
	private String uasSdpPort;
	private String uasSdpCodec;
	private String lastCseq;

        SipCallDisplay(){
            startTime=new Date(Long.MIN_VALUE);
            endTime=new Date(Long.MAX_VALUE);
            callId = "";
            method= "";
            toAorUser= "";
            toName= "";
            fromAorUser= "";
            fromName= "";
            uacIp= "";
            uacPort= "";
            uasIp= "";
            uasPort= "";	
            uacSdpIp= "";
            uacSdpPort= "";
            uacSdpCodec= "";
            uasSdpIp= "";
            uasSdpPort= "";
            uasSdpCodec= "";
            lastCseq= "";
           
        }
        
        public boolean IsAllNull() {
            boolean out = false;
            if(
                this.callId.isEmpty() &&
                this.startTime.equals(new Date(Long.MIN_VALUE)) &&
                this.endTime.equals(new Date(Long.MAX_VALUE)) && 
                this.toAorUser.isEmpty() &&
                this.toName.isEmpty() &&           
                this.fromAorUser.isEmpty() &&
                this.fromName.isEmpty() &&       
                this.uacIp.isEmpty() &&       
                this.uacPort.isEmpty() &&       
                this.uasIp.isEmpty() &&       
                this.uasPort.isEmpty() &&
                this.uacSdpIp.isEmpty() &&        
                this.uacSdpPort.isEmpty() &&       
                this.uacSdpCodec.isEmpty() &&
                this.uasSdpIp.isEmpty() &&
                this.uasSdpPort.isEmpty() &&      
                this.uasSdpCodec.isEmpty() &&
                this.lastCseq.isEmpty()
            ){
                out = true;
            }
            return out;
        }

        public String getCallId() {
            return callId;
        }
        
        public void setCallId(String callId) {
            this.callId = callId;
        }

        public Date getStartTime() {
            return startTime;
        }

        public void setStartTime(Date startTime) {
            this.startTime = startTime;
        }

        public Date getEndTime() {
            return endTime;
        }

        public void setEndTime(Date endTime) {
            this.endTime = endTime;
        }

        public String getToAorUser() {
            return toAorUser;
        }

        public void setToAorUser(String toAorUser) {
            this.toAorUser = toAorUser;
        }

        public String getToName() {
            return toName;
        }

        public void setToName(String toName) {
            this.toName = toName;
        }

        public String getFromAorUser() {
            return fromAorUser;
        }

        public void setFromAorUser(String fromAorUser) {
            this.fromAorUser = fromAorUser;
        }

        public String getFromName() {
            return fromName;
        }

        public void setFromName(String fromName) {
            this.fromName = fromName;
        }

        public String getUacIp() {
            return uacIp;
        }

        public void setUacIp(String uacIp) {
            this.uacIp = uacIp;
        }

        public String getUacPort() {
            return uacPort;
        }

        public void setUacPort(String uacPort) {
            this.uacPort = uacPort;
        }

        public String getUasIp() {
            return uasIp;
        }

        public void setUasIp(String uasIp) {
            this.uasIp = uasIp;
        }

        public String getUasPort() {
            return uasPort;
        }

        public void setUasPort(String uasPort) {
            this.uasPort = uasPort;
        }

        public String getUacSdpIp() {
            return uacSdpIp;
        }

        public void setUacSdpIp(String uacSdpIp) {
            this.uacSdpIp = uacSdpIp;
        }

        public String getUacSdpPort() {
            return uacSdpPort;
        }

        public void setUacSdpPort(String uacSdpPort) {
            this.uacSdpPort = uacSdpPort;
        }

        public String getUacSdpCodec() {
            return uacSdpCodec;
        }

        public void setUacSdpCodec(String uacSdpCodec) {
            this.uacSdpCodec = uacSdpCodec;
        }

        public String getUasSdpIp() {
            return uasSdpIp;
        }

        public void setUasSdpIp(String uasSdpIp) {
            this.uasSdpIp = uasSdpIp;
        }

        public String getUasSdpPort() {
            return uasSdpPort;
        }

        public void setUasSdpPort(String uasSdpPort) {
            this.uasSdpPort = uasSdpPort;
        }

        public String getUasSdpCodec() {
            return uasSdpCodec;
        }

        public void setUasSdpCodec(String uasSdpCodec) {
            this.uasSdpCodec = uasSdpCodec;
        }

        public String getLastCseq() {
            return lastCseq;
        }

        public void setLastCseq(String lastCseq) {
            this.lastCseq = lastCseq;
        }

        public String getMethod() {
            return method;
        }

        public void setMethod(String method) {
            this.method = method;
        }

        public boolean isSelected() {
            return selected;
        }

        public void setSelected(boolean selected) {
            this.selected = selected;
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
                obj.Gui(); 
            } catch (InterruptedException ex) {
                Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
            } catch (IOException ex) {
                Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
}



	



