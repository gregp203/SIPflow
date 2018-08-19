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


import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Button;
import com.googlecode.lanterna.gui2.CheckBoxList;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.GridLayout;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.RadioBoxList;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.gui2.TextGUIThread;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowBasedTextGUI;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogBuilder;
import com.googlecode.lanterna.gui2.dialogs.MessageDialogButton;
import com.googlecode.lanterna.gui2.table.Table;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;
import com.googlecode.lanterna.terminal.TerminalResizeListener;
import java.io.BufferedReader;
import java.io.EOFException;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import static java.lang.Thread.sleep;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SIPflow {
    Screen screen;
    List<SipCall> sipCallList;
    List<String> selectedCallIds;
    private final Object sipCallListMonitor = new Object();
    int numSipMsg;
    private final Object numSipMsgMonitor = new Object();
    Table<String> callTable;
    Label inputStatusLabel;    
    private final Object callTableMonitor = new Object();
    DateFormat tableStartDateFormat;
    DateFormat tableEndDateFormat;
    SimpleDateFormat logDateFormat; 
    SimpleDateFormat FilterDateFormat;
    SipCallDisplay callFilter;
    List<String> methodFilter;
    int newCalls;
    Date minDate;
    Date maxDate;
    private final Object termResizedMonitor = new Object();
    boolean termResized = false;
    
    
    public SIPflow() {      
        sipCallList = new ArrayList<SipCall>();
        selectedCallIds = new ArrayList<String>();
        tableStartDateFormat = new SimpleDateFormat("yyyy-MM-dd'@'HH:mm:ss.SSSXXX");
        tableEndDateFormat = new SimpleDateFormat("yyyy-MM-dd'@'HH:mm:ss");
        logDateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSXXX");
        FilterDateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSXXX");        
        methodFilter = new ArrayList<String>();
        methodFilter.add("INVITE");
        new GuiThread(this);
        newCalls=0;
        numSipMsg=0;
        try {
            minDate = logDateFormat.parse("2000-01-01 00:00:00.000-05:00");
            maxDate = logDateFormat.parse("3000-01-01 00:00:00.000-05:00");
        } catch (ParseException ex) {
            Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
        }
        callFilter = new SipCallDisplay(minDate,maxDate);
    }

    public static void main(String[] args) throws IOException, InterruptedException {		
        SIPflow SIPflowObj = new SIPflow();
        File file = new File(args[0]); 
        SIPflowObj.ReadInput(new FileReader(file));
    }
	
    void Gui() throws InterruptedException, IOException{

        
        try {
            Terminal term = new DefaultTerminalFactory().createTerminal();
            screen = new TerminalScreen(term);
            WindowBasedTextGUI textGUI = new MultiWindowTextGUI(screen);
            screen.startScreen();
            BasicWindow mainWindow = new BasicWindow();
            mainWindow.setHints(Arrays.asList(Window.Hint.NO_DECORATIONS));          
            Panel mainPanel = new Panel();
            Panel callsPanel = new Panel();
            Panel buttonPanel = new Panel(); 
            Panel inputStatusPannel = new Panel();
            inputStatusLabel = new Label("");
            callTable = new Table<String>(
                    "*",
                    "Start Time", 
                    "Time of Last Msg",
                    "Method",
                    "From",
                    "To",
                    "UAC IP",
                    "UAS IP",
                    "Last CSeq",
                    "Call-ID"
            ); 
            TermResizeHandlerClass termResizeHandler = new TermResizeHandlerClass();
            term.addResizeListener(termResizeHandler);
            int numRows =term.getTerminalSize().getRows();
            callTable.setVisibleRows(numRows-12);
            callTable.setSelectAction(()->  {
                    if(callTable
                            .getTableModel()
                            .getCell(0, callTable.getSelectedRow())
                            .equals("[x]")){
                        callTable
                                .getTableModel()
                                .setCell(0, callTable.getSelectedRow(), "[ ]");
                        selectedCallIds
                                .remove(callTable
                                        .getTableModel()
                                        .getCell(9, callTable.getSelectedRow())
                                );
                    }
                    else{
                        callTable
                                .getTableModel()
                                .setCell(0, callTable.getSelectedRow(), "[x]");
                        selectedCallIds
                                .add(callTable
                                        .getTableModel()
                                        .getCell(9, callTable.getSelectedRow())
                                );
                    }
		});    
            Button filterButton = new Button("Filter", ()-> {
                        CallFilterDialog(textGUI);			
            });
            Button flowButton = new Button("Flow", ()-> {
                try {
                    FlowDiagram(screen);
                } catch (IOException ex) {
                    Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
                } catch (InterruptedException ex) {
                    Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
            Button methodButton = new Button("Method", ()-> {
			 CallMethodFilterDiaog(textGUI);
            });
            Button sortButton = new Button("Sort", ()-> {
			 CallSortDialog(textGUI);
            });
            Button refreshButton = new Button("Refresh", ()-> {
			 refreshCallTable();
            });
            mainPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));            
            buttonPanel.setLayoutManager(new LinearLayout(Direction.HORIZONTAL));
            //buttonPanel.addComponent(methodPanel.withBorder(Borders.singleLine("Methods")));
            buttonPanel.addComponent(refreshButton);
            buttonPanel.addComponent(methodButton);
            buttonPanel.addComponent(sortButton);
            buttonPanel.addComponent(filterButton);            
            buttonPanel.addComponent(flowButton);
            inputStatusPannel.addComponent(inputStatusLabel);
            callsPanel.addComponent(callTable);
            mainPanel.addComponent(buttonPanel.withBorder(Borders.singleLine("Buttons")));
            mainPanel.addComponent(inputStatusPannel.withBorder(Borders.singleLine("Input")));
            mainPanel.addComponent(callsPanel.withBorder(Borders.singleLine("Calls"))); 
            mainWindow.setComponent(mainPanel); 
            textGUI.addWindow(mainWindow);
            waitForTextGUI(mainWindow);
            
        } catch (IOException ex) {
            Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    private class TermResizeHandlerClass implements TerminalResizeListener{
        @Override
        public void onResized(Terminal terminal, TerminalSize newSize){
            callTable.setVisibleRows(newSize.getRows()-12);
            synchronized(termResizedMonitor) {termResized = true;}
        }
    }
    
    void waitForTextGUI(BasicWindow window) {
        while(window.getTextGUI() != null) {
            boolean sleep = true;
            TextGUIThread guiThread = window.getTextGUI().getGUIThread();
            
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
                            addToCallTable(sipCallList.get(i),callFilter);
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
        inputStatusLabel
                .setText("Number of SIP messages found : "+ numberOfMsg +" Number of CallIDs found : "+ numberOfCallIds);
    }
    
    void refreshCallTable(){
        selectedCallIds.clear();        
        synchronized(callTableMonitor){
            int tableLength = callTable.getTableModel().getRowCount();
            for (int i =tableLength; i>0; i--) {
                callTable.getTableModel().removeRow(0);
            } 
        }
        synchronized(sipCallListMonitor){
            for(int i= 0; i<sipCallList.size();i++){
                addToCallTable(sipCallList.get(i),callFilter);
            }
        }
    }
    
    private void addToCallTable(SipCall inputCall,SipCallDisplay filter){
            boolean callMatches = false;
            if(methodFilter.contains(inputCall.getMethod())){
                if(filter.IsAllNull(minDate,maxDate)) callMatches = true;
                if(!callMatches && !filter.getCallId().isEmpty() && inputCall.getCallId().contains(filter.getCallId())) callMatches = true;
                if(!callMatches && !filter.getStartTime().equals(minDate) && inputCall.getStartTime().after(filter.getStartTime())) callMatches = true;
                if( inputCall.getEndTime()!=null && !callMatches && !filter.getEndTime().equals(maxDate) && inputCall.getEndTime().before(filter.getEndTime()) ) callMatches = true;
                if(!callMatches && !filter.getToAorUser().isEmpty() && inputCall.getToAorUser().contains(filter.getToAorUser())) callMatches = true;
                if(!callMatches && !filter.getToName().isEmpty() && inputCall.getToName().contains(filter.getToName())) callMatches = true;
                if(!callMatches && !filter.getFromAorUser().isEmpty() && inputCall.getFromAorUser().contains(filter.getFromAorUser())) callMatches = true;
                if(!callMatches && !filter.getFromName().isEmpty() && inputCall.getFromName().contains(filter.getFromName())) callMatches = true;
                if(!callMatches && !filter.getUacIp().isEmpty() && inputCall.getUacIp().contains(filter.getUacIp())) callMatches = true;
                if(!callMatches && !filter.getUacPort().isEmpty() && inputCall.getUacPort().contains(filter.getUacPort())) callMatches = true;
                if(!callMatches && !filter.getUasIp().isEmpty() && inputCall.getUasIp().contains(filter.getUasIp())) callMatches = true;
                if(!callMatches && !filter.getUasPort().isEmpty() && inputCall.getUasPort().contains(filter.getUasPort())) callMatches = true;                
                if(!callMatches && !filter.getUacSdpIp().isEmpty() && inputCall.getUacSdpIp().contains(filter.getUacSdpIp())) callMatches = true;
                if(!callMatches && !filter.getUacSdpPort().isEmpty() && inputCall.getUacSdpPort().contains(filter.getUacSdpPort())) callMatches = true;
                if(!callMatches && !filter.getUacSdpCodec().isEmpty() && inputCall.getUacSdpCodec().contains(filter.getUacSdpCodec())) callMatches = true;
                if(!callMatches && !filter.getUasSdpIp().isEmpty() && inputCall.getUasSdpIp().contains(filter.getUasSdpIp())) callMatches = true;
                if(!callMatches && !filter.getUasSdpPort().isEmpty() && inputCall.getUasSdpPort().contains(filter.getUasSdpPort())) callMatches = true;
                if(!callMatches && !filter.getUasSdpCodec().isEmpty() && inputCall.getUasSdpCodec().contains(filter.getUasSdpCodec())) callMatches = true;
                if(!callMatches && !filter.getLastCseq().isEmpty() && inputCall.getLastCseq().contains(filter.getLastCseq())) callMatches = true;
            }
            if(callMatches){
                
                {    
                    callTable.getTableModel().addRow(
                            "[ ]",
                            tableStartDateFormat.format(inputCall.getStartTime()),
                            inputCall.getEndTime()!=null ? tableEndDateFormat.format(inputCall.getEndTime()):null,
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
    
    void CallMethodFilterDiaog(WindowBasedTextGUI inputTextGUI){
        BasicWindow methodWindow = new BasicWindow();
        Panel methodPanel= new Panel();
        TerminalSize methodCheckBoxsize = new TerminalSize(16, 5);
        CheckBoxList<String> methodCheckBox= new CheckBoxList<String>(methodCheckBoxsize);
        methodCheckBox.addItem("INVITE");
        methodCheckBox.addItem("NOTIFY");
        methodCheckBox.addItem("OPTIONS");
        methodCheckBox.addItem("REGISTER");
        methodCheckBox.addItem("SUBSCRIBE");
        methodCheckBox.setChecked("INVITE", methodFilter.contains("INVITE"));
        methodCheckBox.setChecked("NOTIFY", methodFilter.contains("NOTIFY"));
        methodCheckBox.setChecked("OPTIONS", methodFilter.contains("OPTIONS"));
        methodCheckBox.setChecked("REGISTER", methodFilter.contains("REGISTER"));
        methodCheckBox.setChecked("SUBSCRIBE", methodFilter.contains("SUBSCRIBE"));
        Button methodApplylButton = new Button("Apply", ()-> {
                    methodFilter.clear();
                    methodFilter = methodCheckBox.getCheckedItems();    
                    refreshCallTable();
                    methodWindow.close();
            });
        Button methodCancelButton = new Button("Cancel", ()-> {
                methodWindow.close();
        });
        methodPanel.addComponent(methodCheckBox);
        methodPanel.addComponent(methodApplylButton);
        methodPanel.addComponent(methodCancelButton);
        methodWindow.setComponent(methodPanel.withBorder(Borders.singleLine("Method")));
        inputTextGUI.addWindow(methodWindow);
        waitForTextGUI(methodWindow);
    }
    
    void CallSortDialog(WindowBasedTextGUI inputTextGUI){
        BasicWindow sortWindow = new BasicWindow();
        Panel sortPanel= new Panel();
        TerminalSize sortRadioBoxSize = new TerminalSize(16, 4);
            RadioBoxList<String> sortRadioBox = new RadioBoxList<String>(sortRadioBoxSize);
            sortRadioBox.addItem("Start Time");
            sortRadioBox.addItem("End Time");
            sortRadioBox.addItem("From");
            sortRadioBox.addItem("To");
            sortRadioBox.setCheckedItem("Start Time");
            Button sortApplylButton = new Button("Apply", ()-> {
                    synchronized(sipCallListMonitor){
                        switch (sortRadioBox.getCheckedItem()){
                            
                            case "Start Time" :
                                sipCallList.sort((call1, call2) -> {
                                        return call1.getStartTime().compareTo(call2.getStartTime());
                                    });
                                break;
                            
                            case "End Time" :
                                sipCallList.sort((call1, call2) -> {
                                        return call1.getEndTime().compareTo(call2.getEndTime());
                                    });
                                break;
                                
                            case "From" :
                                sipCallList.sort((call1, call2) -> {
                                        return call1.getFromAorUser().compareTo(call2.getFromAorUser());
                                    });
                                break;
                                
                            case "To" :
                                sipCallList.sort((call1, call2) -> {
                                        return call1.getToAorUser().compareTo(call2.getToAorUser());
                                    });
                                break;
                        }        
                    }
                    refreshCallTable();
                    sortWindow.close();
            });
            Button sortCancelButton = new Button("Cancel", ()-> {
                    sortWindow.close();
            });
            sortPanel.setLayoutManager(new LinearLayout(Direction.VERTICAL));
            sortPanel.addComponent(sortRadioBox);
            sortPanel.addComponent(sortApplylButton);
            sortPanel.addComponent(sortCancelButton);
            sortWindow.setComponent(sortPanel.withBorder(Borders.singleLine("Sort")));
            inputTextGUI.addWindow(sortWindow);
            waitForTextGUI(sortWindow);
    }

    void CallFilterDialog(WindowBasedTextGUI inputTextGUI){
        BasicWindow filterWindow = new BasicWindow("Filter");
        TimeTxtBox startTimeTxtBox = new TimeTxtBox(new TerminalSize(30,1),inputTextGUI,FilterDateFormat.format(callFilter.getStartTime()));
        TextBox endTimeTxtBox = new TextBox(new TerminalSize(30,1),FilterDateFormat.format(callFilter.getEndTime()));
        TextBox fromUserAorTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getFromAorUser());
        TextBox fromUserNameTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getFromName());
        TextBox toUserAorTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getToAorUser());
        TextBox toUserNameTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getToName());
        TextBox uacIpTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getUacIp());
        TextBox uacPortTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getUacPort());
        TextBox uacSdpIpTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getUacSdpIp());
        TextBox uacSdpPortTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getUacSdpPort());
        TextBox uasIpTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getUasIp());
        TextBox uasPortTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getUasPort());
        TextBox uasSdpIpTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getUasSdpIp());
        TextBox uasSdpPortTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getUasSdpPort());
        TextBox callIdTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getCallId());                   
        TextBox lastCseqTxtBox = new TextBox(new TerminalSize(30,1), callFilter.getLastCseq());
        Button filterApplyButton;
        filterApplyButton = new Button("Apply", ()-> {
            try {
                if (!startTimeTxtBox.getText().isEmpty() &&
                        !logDateFormat.parse(startTimeTxtBox.getText()).equals(minDate) ) {
                    callFilter.setStartTime(logDateFormat.parse(startTimeTxtBox.getText()));
                }
                
                if (!endTimeTxtBox.getText().isEmpty()  &&
                        !logDateFormat.parse(endTimeTxtBox.getText()).equals(maxDate) ) {
                    callFilter.setEndTime(logDateFormat.parse(endTimeTxtBox.getText()));
                }
                callFilter.setFromAorUser(fromUserAorTxtBox.getText());
                callFilter.setFromName(fromUserNameTxtBox.getText());
                callFilter.setToAorUser(toUserAorTxtBox.getText());
                callFilter.setToName(toUserNameTxtBox.getText());
                callFilter.setUacIp(uacIpTxtBox.getText());                                
                callFilter.setUacPort(uacPortTxtBox.getText());
                callFilter.setUacSdpIp(uacSdpIpTxtBox.getText());
                callFilter.setUacSdpPort(uacSdpPortTxtBox.getText());
                callFilter.setUasIp(uasIpTxtBox.getText());
                callFilter.setUasPort(uasPortTxtBox.getText());
                callFilter.setUasSdpIp(uasSdpIpTxtBox.getText());
                callFilter.setUasSdpPort(uasSdpPortTxtBox.getText());
                callFilter.setCallId(callIdTxtBox.getText());
                callFilter.setLastCseq(lastCseqTxtBox.getText());
                refreshCallTable();
                filterWindow.close();
            } catch (ParseException ex) {
                Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
            }
        }); 
        Button filterCancelButton = new Button("Cancel", ()-> {
            filterWindow.close();
        });
        Panel filterPanel = new Panel();
        filterPanel.setLayoutManager(new GridLayout(2));
        filterPanel.addComponent(new Label("Start Time : "));
        filterPanel.addComponent(startTimeTxtBox);
        filterPanel.addComponent(new Label("Last Msg Time : "));
        filterPanel.addComponent(endTimeTxtBox);
        filterPanel.addComponent(new Label("From: User Aor : "));
        filterPanel.addComponent(fromUserAorTxtBox);
        filterPanel.addComponent(new Label("From: Name : "));
        filterPanel.addComponent(fromUserNameTxtBox);
        filterPanel.addComponent(new Label("To: User Aor : "));
        filterPanel.addComponent(toUserAorTxtBox);
        filterPanel.addComponent(new Label("To: Name : "));
        filterPanel.addComponent(toUserNameTxtBox);
        filterPanel.addComponent(new Label("UAC IP : "));
        filterPanel.addComponent(uacIpTxtBox);
        filterPanel.addComponent(new Label("UAC Port : "));
        filterPanel.addComponent(uacPortTxtBox);
        filterPanel.addComponent(new Label("UAC SDP IP : "));
        filterPanel.addComponent(uacSdpIpTxtBox);
        filterPanel.addComponent(new Label("UAC SDP Port : "));
        filterPanel.addComponent(uacSdpPortTxtBox);
        filterPanel.addComponent(new Label("UAS IP : "));
        filterPanel.addComponent(uasIpTxtBox);
        filterPanel.addComponent(new Label("UAS Port : "));
        filterPanel.addComponent(uasPortTxtBox);
        filterPanel.addComponent(new Label("UAS SDP IP : "));
        filterPanel.addComponent(uasSdpIpTxtBox);
        filterPanel.addComponent(new Label("UAS SDP Port : "));
        filterPanel.addComponent(uasSdpPortTxtBox);
        filterPanel.addComponent(new Label("Call-ID : "));
        filterPanel.addComponent(callIdTxtBox);
        filterPanel.addComponent(new Label("Last CSeq : "));
        filterPanel.addComponent(lastCseqTxtBox);
        filterPanel.addComponent(filterApplyButton);
        filterPanel.addComponent(filterCancelButton);
        filterWindow.setComponent(filterPanel);
        inputTextGUI.addWindow(filterWindow);
        waitForTextGUI(filterWindow);
    }
    
    private class TimeTxtBox extends TextBox {
        WindowBasedTextGUI textGUI;
        TimeTxtBox(TerminalSize preferredSize,WindowBasedTextGUI inputTextGUI, String initialContent){
            super(preferredSize,initialContent);
            textGUI = inputTextGUI;
        }
        
        @Override
        protected void afterLeaveFocus(FocusChangeDirection direction, Interactable previouslyInFocus){
            if (!Pattern.compile("(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3})((-|\\+)\\d{2}:\\d{2})").matcher(getText()).find()){
            new MessageDialogBuilder()
		.setTitle("Incorrect Date Format")
		.setText("The date has to be in the format \nof yyyy-MM-dd HH:mm:ss.SSS+00:00")
		.addButton(MessageDialogButton.Close)
                .setExtraWindowHints(Arrays.asList(Window.Hint.CENTERED))
		.build()
		.showDialog(textGUI);
            setText(FilterDateFormat.format(callFilter.getStartTime()));
            }
        }
    }
    
    void FlowDiagram(Screen inputScreen) throws IOException, InterruptedException{
        //list of colors
        TextColor[] flowColor = { 
        TextColor.ANSI.CYAN,
        TextColor.ANSI.MAGENTA,
        TextColor.ANSI.YELLOW,
        TextColor.ANSI.GREEN,
        TextColor.ANSI.RED
        };
        //assemble list of messages to diagram
        List<SipMessage> diagramMessges = new ArrayList<SipMessage>();
        int i=5;
        synchronized(sipCallListMonitor){
            for(SipCall call :sipCallList ){
                TextColor color = flowColor[i % 5];
                i++;
                if(selectedCallIds.contains(call.getCallId())){
                    for(SipMessage sipMsg :call.getSipMessages() ){
                        if( !sipMsg.getSrcIp().equals( sipMsg.getDstIp() ) ){
                            sipMsg.setColor(color);
                            diagramMessges.add(sipMsg);
                        }
                    }
                }
            }
        }
        //sort the list of messages to diagram by time        
        diagramMessges.sort((msg1, msg2) -> {
                                        return msg1.getTimeDateStamp().compareTo(msg2.getTimeDateStamp());
                                    });
        //find all IPaddresses 
        int segmentLenght = 20;  // can't be smaller than 13
        List<String> ipList = new ArrayList<String>();
        for(SipMessage msg : diagramMessges){
            if(!ipList.contains(msg.getSrcIp())){
                ipList.add(msg.getSrcIp());
            } 
            if(!ipList.contains(msg.getDstIp())){
                ipList.add(msg.getDstIp());                
            }
        }
        //draw the flow diagram
        int verticalPosition = 0;
        int horizonatalPosition = 0;
        DrawFlow (inputScreen, diagramMessges, ipList,verticalPosition,horizonatalPosition, segmentLenght);
        inputScreen.refresh();
        //poll for keyboardinput
        boolean done = false;
        while(!done){
            synchronized(termResizedMonitor){
                if (termResized){
                    inputScreen.doResizeIfNecessary();
                    DrawFlow (inputScreen, diagramMessges, ipList ,verticalPosition,horizonatalPosition,segmentLenght);
                    termResized = false;
                }
            }
            KeyStroke keyStroke = screen.pollInput();
            if(keyStroke !=null){
                switch(keyStroke.getKeyType()){
                    case ArrowDown : 
                        if (verticalPosition<diagramMessges.size()-1) verticalPosition++;
                        break;
                    case ArrowUp :
                        if (verticalPosition >0) verticalPosition--;
                        break;
                    case Escape:
                    case EOF:
                        done = true;
                        break;
                    case ArrowRight:
                        if (horizonatalPosition < (19 + (ipList.size()*segmentLenght))-inputScreen.getTerminalSize().getColumns()) horizonatalPosition++;
                        break;
                    case ArrowLeft:
                        if (horizonatalPosition>0) horizonatalPosition--;
                        break;
                }
                DrawFlow (inputScreen, diagramMessges, ipList ,verticalPosition,horizonatalPosition ,segmentLenght);
            }
            sleep(12);
        }
    }

    void DrawFlow (Screen inputScreen
            , List<SipMessage> diagramMessges
            , List<String> ipList, int verticalPosition
            , int horizontalPosition
            , int segmentLenght) 
            throws IOException{
        //create header string
        String ipHeader="Time         ";
        for(String ip:ipList){
            ipHeader += ip + StringRepeat(" ",segmentLenght-ip.length());
        }
        inputScreen.clear(); 
        inputScreen.setCursorPosition(null);
        //ip addresses at top of screen 
        inputScreen.newTextGraphics()
                .putString(0, 0, ipHeader
                        .substring(Math.min(
                                horizontalPosition
                                ,ipHeader.length()
                        )
                        )
                );
        inputScreen.newTextGraphics()
                .putString(0, 1, 
                        StringRepeat("-",19 + (ipList.size()*segmentLenght) )
                                .substring(Math.min(horizontalPosition
                                        ,19 + (ipList.size()*segmentLenght)
                                )
                                )
                );
        //loop trough diagramMessges and write them line at a time
        int firstLine = Math.max(
                Math.min(
                    Math.max((verticalPosition-inputScreen.getTerminalSize().getRows()/2), 
                        0),
                    diagramMessges.size()-inputScreen.getTerminalSize().getRows()+2 ),
                0);
        int lastLine = Math.min(diagramMessges.size(),firstLine + inputScreen.getTerminalSize().getRows());
        for (int i = firstLine;i < lastLine;i++){
            //inputScreen.newTextGraphics().putString(0, i+2, MessageLine (diagramMessges.get(i),ipList,segmentLenght));
            MessageLine (inputScreen, i-firstLine+2, horizontalPosition, diagramMessges.get(i),ipList,segmentLenght,false);
        }
        //reverse cursur position 
        MessageLine (inputScreen
                ,2+verticalPosition-firstLine
                , horizontalPosition
                ,diagramMessges.get(verticalPosition)
                ,ipList,20,true);
        inputScreen.refresh();
    }
    
    static void MessageLine (Screen inputScreen
            ,int verticalPosition
            , int horizontalPosition
            , SipMessage inputSipMsg
            ,List<String> inputIpList
            ,int inputsegmentLenght
            ,boolean reverse) {
        String leftString = "";        
        //get ip addresses of the msg and thier index and direction from ipList
        int lowIndex = Math.min( inputIpList.indexOf( inputSipMsg.getSrcIp() )
                ,inputIpList.indexOf( inputSipMsg.getDstIp() ) );
        int highIndex = Math.max( inputIpList.indexOf( inputSipMsg.getSrcIp() )
                ,inputIpList.indexOf( inputSipMsg.getDstIp() ) );
        boolean pointsRight = inputIpList.indexOf( inputSipMsg.getSrcIp() ) 
                < inputIpList.indexOf( inputSipMsg.getDstIp() );
        //add date to output        
        leftString += new SimpleDateFormat("HH:mm:ss.SSSXXX")
                .format(inputSipMsg.getTimeDateStamp())+"|";
        //add left white space
        leftString += StringRepeat(StringRepeat(" ",inputsegmentLenght-1) +"|",lowIndex-0);
        //calculate string length per horizontal position
        int hzpLeftString = Math.min(horizontalPosition,leftString.length());
        //draw left string
        if(reverse){
            inputScreen
                    .newTextGraphics()
                    .putString(0,verticalPosition,leftString.substring(hzpLeftString),SGR.REVERSE);
        }else{
            inputScreen
                    .newTextGraphics()
                    .putString(0,verticalPosition,leftString.substring(hzpLeftString));
        }
        String middleString ="";
        //add left arrow point or not   
        middleString += pointsRight ? "-" : "<";
        //determin part of sip msg to diplay
        String displayedPartOfSipMsg = coalesceString(
                inputSipMsg.getMethod(),
                inputSipMsg.getResponse())
                    .substring( 0,
                            Math.min( 10, coalesceString( inputSipMsg.getMethod(),
                                inputSipMsg.getResponse() ).length() ));
        //draw left part of line
        //determin lenght of left line:  the full length of line which is the 
        //difference between the index number times the segment length - 1 for 
        //the vert line on the end - 2 for the points - the displayed part of 
        //the sip msg all in half and whatever 
        //is leftover will be used for the right line
        int leftLineLength = (((highIndex-lowIndex)*inputsegmentLenght)-1-2-displayedPartOfSipMsg.length())/2;
        middleString += StringRepeat("-", leftLineLength);
        //add the displayed part of the sip msg
        middleString += displayedPartOfSipMsg;
        //draw right part of line
        //determin lenght of right line: 
        ///the full length of line (highIndex-lowIndex*segmentLenght)
        // - 1 for the vert line on the end - 2 for the points
        // - displayedPartOfSipMsg.length() - leftLineLength
        int rightLineLength = ((highIndex-lowIndex)*inputsegmentLenght)-1-2-displayedPartOfSipMsg.length()-leftLineLength ;
        middleString += StringRepeat("-", rightLineLength);        
        //add right arrow point or not
        middleString += pointsRight ? ">" : "-";
        //calculate length of middlestring per horizontalposision       
        int hzpMidString = Math.min(horizontalPosition-hzpLeftString,middleString.length());
        //draw middleString
        if(reverse){
            inputScreen.newTextGraphics().setForegroundColor(inputSipMsg.getColor())
                    .putString(leftString.length()-hzpLeftString
                            ,verticalPosition
                            ,middleString
                                    .substring(hzpMidString)
                                , SGR.REVERSE 
                    );
        }else{
            inputScreen.newTextGraphics().setForegroundColor(inputSipMsg.getColor())
                    .putString(leftString.length()-hzpLeftString
                            ,verticalPosition
                            ,middleString
                                    .substring(hzpMidString)
                    );
        }
        //TODO revert color back to default 
        String rightString = "";
        //add vert line
        rightString += "|"; 
        //add right white space
        rightString += StringRepeat(StringRepeat(" ",inputsegmentLenght-1) +"|",inputIpList.size()-1-highIndex);
        //calculate length of middlestring per horizontalposision       
        int hzpRightString = Math.min(horizontalPosition-hzpLeftString-hzpMidString,rightString.length());        
        //draw rightString
        if(reverse){
            inputScreen.newTextGraphics()
                    .putString((leftString.length()-hzpLeftString) + (middleString.length()-hzpMidString)
                            ,verticalPosition
                            ,rightString
                                    .substring(hzpRightString)
                            ,SGR.REVERSE
                    );
        }else{
            inputScreen.newTextGraphics()
                    .putString((leftString.length()-hzpLeftString) + (middleString.length()-hzpMidString)
                            ,verticalPosition
                            ,rightString
                                    .substring(hzpRightString)
                    );
        }
    }
    
    static String StringRepeat (String inputString,int inputNumberOfTimes){
        String output = "";
        for (int i = 0; i < inputNumberOfTimes; i++) {
            output += inputString;
        }
        return output;
    }
    
    static String coalesceString (String a, String b) {
        
        return a != null ? a : b;
    }

    
    void ReadInput(Reader inputReader) throws InterruptedException {
        //wait for GUI thread to be ready
        
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
            if (inputSipMessage.getTimeDateStamp().compareTo(startTime) > 0) endTime = inputSipMessage.getTimeDateStamp();
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
	private TextColor color;
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

        SipCallDisplay(Date min,Date max){
            startTime = min;
            endTime = max;
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
        
        public boolean IsAllNull(Date min,Date max) {
            boolean out = false;
            if(
                this.callId.isEmpty() &&
                this.startTime.equals(min) &&
                this.endTime.equals(max) && 
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



	



