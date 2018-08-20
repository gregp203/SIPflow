/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.mycompany.sipflow;

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
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
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
import java.io.EOFException;
import java.io.IOException;
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
import java.util.regex.Pattern;

/**
 *
 * @author palmerg
 */
public class Gui {
    DateFormat tableStartDateFormat;
    DateFormat tableEndDateFormat;
    SimpleDateFormat FilterDateFormat;
    SimpleDateFormat logDateFormat; 
    SipCallDisplay callFilter;
    List<String> methodFilter;
    Date minDate;
    Date maxDate;
    private final Object termResizedMonitor = new Object();
    boolean termResized = false;
    TerminalSize newTermSize;
    SIPflow mainSIPflowObject;
    
    Gui (SIPflow sIPflow) throws InterruptedException, IOException{
        mainSIPflowObject = sIPflow;
        tableStartDateFormat = new SimpleDateFormat("yyyy-MM-dd'@'HH:mm:ss.SSSXXX");
        tableEndDateFormat = new SimpleDateFormat("HH:mm:ss");
        FilterDateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSXXX");
        logDateFormat = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSXXX");        
        methodFilter = new ArrayList<String>();
        methodFilter.add("INVITE");
        try {
            minDate = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSXXX")
                    .parse("2000-01-01 00:00:00.000-05:00");
            maxDate = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss.SSSXXX")
                    .parse("3000-01-01 00:00:00.000-05:00");
        } catch (ParseException ex) {
            Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
        }
        callFilter = new SipCallDisplay(minDate,maxDate);
        List<String> selectedCallIds = new ArrayList<String>();
        
        try {
            Terminal term = new DefaultTerminalFactory().createTerminal();
            Screen screen = new TerminalScreen(term);
            WindowBasedTextGUI textGUI = new MultiWindowTextGUI(screen);
            screen.startScreen();
            BasicWindow mainWindow = new BasicWindow();
            mainWindow.setHints(Arrays.asList(Window.Hint.NO_DECORATIONS));          
            Panel mainPanel = new Panel();
            Panel callsPanel = new Panel();
            Panel buttonPanel = new Panel(); 
            Panel inputStatusPannel = new Panel();
            Label inputStatusLabel = new Label("");
            Table<String> callTable = new Table<String>(
                    "*",
                    "Start Time", 
                    "Last Msg",
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
                        CallFilterDialog(textGUI,selectedCallIds,callTable);			
            });
            Button flowButton = new Button("Flow", ()-> {
                try {
                    if (selectedCallIds.size()>0){
                        FlowDiagram(screen,selectedCallIds);
                    }
                } catch (IOException | InterruptedException ex) {
                    Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
                }
            });
            Button methodButton = new Button("Method", ()-> {
			 CallMethodFilterDiaog(textGUI,selectedCallIds, callTable);
            });
            Button sortButton = new Button("Sort", ()-> {
			 CallSortDialog(textGUI,selectedCallIds,callTable);
            });
            Button refreshButton = new Button("Refresh", ()-> {
			 refreshCallTable(selectedCallIds,callTable);
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
            waitForTextGUI(mainWindow,callTable,inputStatusLabel);
            
        } catch (IOException ex) {
            Logger.getLogger(SIPflow.class.getName()).log(Level.SEVERE, null, ex);
        }
    }
    
    void waitForTextGUI(BasicWindow window,Table<String> callTable,Label inputStatusLabel) {
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
                    synchronized(mainSIPflowObject.callTableMonitor){
                        for(;mainSIPflowObject.newCalls >0;mainSIPflowObject.newCalls--){
                            int i = mainSIPflowObject.sipCallList.size() - mainSIPflowObject.newCalls;
                            addToCallTable(mainSIPflowObject.sipCallList.get(i),callFilter,callTable);
                        }
                    }
                    synchronized(termResizedMonitor){
                        if(termResized){
                            callTable.setVisibleRows(newTermSize.getRows()-12);
                        }
                    }
                    updateInputStauts(inputStatusLabel);
                }
                catch(InterruptedException ignore) {}
            }
        }
    }
    
    void updateInputStauts(Label inputStatusLabel){        
        int numberOfMsg;
        int numberOfCallIds;
        synchronized(mainSIPflowObject.numSipMsgMonitor) {numberOfMsg = mainSIPflowObject.numSipMsg;}
        synchronized(mainSIPflowObject.sipCallListMonitor){numberOfCallIds = mainSIPflowObject.sipCallList.size();}
        inputStatusLabel
                .setText("Number of SIP messages found : "+ numberOfMsg +" Number of CallIDs found : "+ numberOfCallIds);
    }
    
    void refreshCallTable(List<String> selectedCallIds,Table<String> callTable){
        selectedCallIds.clear();        
        synchronized(mainSIPflowObject.callTableMonitor){
            int tableLength = callTable.getTableModel().getRowCount();
            for (int i =tableLength; i>0; i--) {
                callTable.getTableModel().removeRow(0);
            } 
        }
        synchronized(mainSIPflowObject.sipCallListMonitor){
            for(int i= 0; i<mainSIPflowObject.sipCallList.size();i++){
                addToCallTable(mainSIPflowObject.sipCallList.get(i),callFilter,callTable);
            }
        }
    }
    
    private void addToCallTable(SIPflow.SipCall inputCall,SipCallDisplay filter,Table<String> callTable){
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
    
    void CallMethodFilterDiaog(WindowBasedTextGUI inputTextGUI,List<String> selectedCallIds,Table<String> callTable){
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
                    refreshCallTable(selectedCallIds,callTable);
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
    }
    
    void CallSortDialog(WindowBasedTextGUI inputTextGUI,List<String> selectedCallIds,Table<String> callTable){
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
                    synchronized(mainSIPflowObject.sipCallListMonitor){
                        switch (sortRadioBox.getCheckedItem()){
                            
                            case "Start Time" :
                                mainSIPflowObject.sipCallList.sort((call1, call2) -> {
                                        return call1.getStartTime().compareTo(call2.getStartTime());
                                    });
                                break;
                            
                            case "End Time" :
                                mainSIPflowObject.sipCallList.sort((call1, call2) -> {
                                        return call1.getEndTime().compareTo(call2.getEndTime());
                                    });
                                break;
                                
                            case "From" :
                                mainSIPflowObject.sipCallList.sort((call1, call2) -> {
                                        return call1.getFromAorUser().compareTo(call2.getFromAorUser());
                                    });
                                break;
                                
                            case "To" :
                                mainSIPflowObject.sipCallList.sort((call1, call2) -> {
                                        return call1.getToAorUser().compareTo(call2.getToAorUser());
                                    });
                                break;
                        }        
                    }
                    refreshCallTable(selectedCallIds,callTable);
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
    }

    void CallFilterDialog(WindowBasedTextGUI inputTextGUI,List<String> selectedCallIds,Table<String> callTable){
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
                refreshCallTable(selectedCallIds, callTable);
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
    }
    
    private class TermResizeHandlerClass implements TerminalResizeListener{
        @Override
        public void onResized(Terminal terminal, TerminalSize newSize){
            synchronized(termResizedMonitor) {
                termResized = true;
                newTermSize = newSize;
            }
        }
    }
    
    private class TimeTxtBox extends TextBox {
        WindowBasedTextGUI textGUI;
        TimeTxtBox(TerminalSize preferredSize,WindowBasedTextGUI inputTextGUI, String initialContent){
            super(preferredSize,initialContent);
            textGUI = inputTextGUI;
        }
        
        @Override
        protected void afterLeaveFocus(Interactable.FocusChangeDirection direction, Interactable previouslyInFocus){
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
    
    void FlowDiagram(Screen screen,List<String> selectedCallIds) throws IOException, InterruptedException{
        //list of colors
        TextColor[] flowColor = { 
        TextColor.ANSI.CYAN,
        TextColor.ANSI.MAGENTA,
        TextColor.ANSI.YELLOW,
        TextColor.ANSI.GREEN,
        TextColor.ANSI.RED
        };
        //assemble list of messages to diagram
        List<SIPflow.SipMessage> diagramMessges = new ArrayList<SIPflow.SipMessage>();
        int i=5;
        synchronized(mainSIPflowObject.sipCallListMonitor){
            for(SIPflow.SipCall call :mainSIPflowObject.sipCallList ){
                TextColor color = flowColor[i % 5];
                i++;
                if(selectedCallIds.contains(call.getCallId())){
                    for (SIPflow.SipMessage sipMsg : call.getSipMessages()) {
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
        for(SIPflow.SipMessage msg : diagramMessges){
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
        DrawFlow (screen, diagramMessges, ipList,verticalPosition,horizonatalPosition, segmentLenght);
        screen.refresh();
        //poll for keyboardinput
        boolean done = false;
        while(!done){
            synchronized(termResizedMonitor){
                if (termResized){
                    screen.doResizeIfNecessary();
                    DrawFlow (screen, diagramMessges, ipList ,verticalPosition,horizonatalPosition,segmentLenght);
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
                        if (horizonatalPosition < (19 + (ipList.size()*segmentLenght))-screen.getTerminalSize().getColumns()) horizonatalPosition++;
                        break;
                    case ArrowLeft:
                        if (horizonatalPosition>0) horizonatalPosition--;
                        break;
                    case Enter:
                        SipMessage(screen,diagramMessges,verticalPosition);
                        break;
                }
                DrawFlow (screen, diagramMessges, ipList ,verticalPosition,horizonatalPosition ,segmentLenght);
            }
            sleep(12);
        }
    }
    
    void SipMessage(Screen screen,List<SIPflow.SipMessage> diagramMessges,int verticalPosition) throws IOException, InterruptedException{
        String[] msg = diagramMessges.get(verticalPosition).getMessage().split("\n");
         
        screen.setCursorPosition(null);
        for (int i = 0; i < msg.length ;i++){
            screen.newTextGraphics().putString(0, i, msg[i]);
        }
        boolean doneMsg = false;
        int msgVertPos = 0;
        int msgHorzPos =0;
        DrawSipMessage(screen,msg, msgVertPos,msgHorzPos); 
        while(!doneMsg){
            synchronized(termResizedMonitor){
                if (termResized){
                    screen.doResizeIfNecessary();
                    DrawSipMessage(screen,msg, msgVertPos,msgHorzPos);
                    termResized = false;
                }
            }
            KeyStroke keyStroke = screen.pollInput();
            if(keyStroke !=null){
                switch(keyStroke.getKeyType()){
                    case ArrowDown : 
                        if (msgVertPos < msg.length-1) msgVertPos++;
                        break;
                    case ArrowUp :
                        if (msgVertPos >0) msgVertPos--;
                        break;
                    case Escape:
                    case EOF:
                        doneMsg = true;
                        break;
                    case ArrowRight:
                        msgHorzPos++;
                        break;
                    case ArrowLeft:
                        if (msgHorzPos>0) msgHorzPos--;
                        break;
                }
                DrawSipMessage(screen,msg, msgVertPos,msgHorzPos);
            }
            sleep(12);
        }
        
    }
    
    void DrawSipMessage(Screen screen,String[] msg,int msgVertPos,int msgHorzPos) throws IOException{
        int row =0;
        screen.clear();
        for (int i = msgVertPos; i < msg.length ;i++){
            screen.newTextGraphics().putString(0, row, msg[i].substring(Math.min(msg[i].length(),msgHorzPos)));
            row++;
        }
        screen.refresh();
    }
    
    void DrawFlow (Screen inputScreen
            , List<SIPflow.SipMessage> diagramMessges
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
            , SIPflow.SipMessage inputSipMsg
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
        if(inputSipMsg.hasSdp()){
            displayedPartOfSipMsg+="-SDP";
        }
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
}
