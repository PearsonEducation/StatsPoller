'****************About Section**************** 
'
'  Writes desired command & calculations into output file.
'  
'  Output file format:  MetricPath MetricValue EpochTimestamp
'      MetricPath:  String representing Metric.
'           "." separates sub-groupings.
'           Should include unit of measurement.
'               eg:  CPU.User-%
'      MetricValue:  Numeric value of the output.
'           "." acceptable in the value.
'               eg:  2234
'               eg:  2234.12345
'      EpochTimestamp:  Time that measurement was taken.
'               eg:  1383148462
'  
'  Suggested Directory Structure
'      .../StatsPoller/output   -> Default location of output
'	   .../StatsPoller/bin      -> Default location of vbscripts
'  
'  When calling from the command line
'    the following parameters are accepted and are optional.
'        Output directory (Argument 1). Path only
'        Output file (Argument 2).  File name only.
'             eg.  cscript command_poller.vbs ..\output\ command_poller_output.out
'
'  Scripts may have programmed delays.  Consider this when setting up run frequency.
'  
'  Author:  Judah Walker
'
'**************End About Section***************

'***********Output Location Section************
Option Explicit
On Error Goto 0

Dim strSrv, strQuery
strSrv = "."
Dim objShell, objFSO, objFile, args, outputlocation, outputfile, file
Set objShell = Wscript.CreateObject("WScript.Shell")
Set objFSO = CreateObject("Scripting.FileSystemObject")

args = WScript.Arguments.Count

outputlocation = ".\output\"
outputfile = "sqlserver-buffer_poller_output.out"
If args = 1 Then
outputlocation = WScript.Arguments.Item(1)
outputfile = "sqlserver-buffer_poller_output.out"
ElseIf args = 2 Then
outputlocation = WScript.Arguments.Item(1)
outputfile = WScript.Arguments.Item(2)
End If 

file = outputlocation & outputfile

Set objFile = objFSO.CreateTextFile(file, True)
GetBuffer(StrSrv)
'**********End Output Location Section*********

'**********Epoch Time Compute Section**********
Function TimeStamp()
	Dim myDateString 
	myDateString = Now()
	Dim SecsSince
	SecsSince = CLng(DateDiff("s", "01/01/1970 00:00:00", myDateString))
	TimeStamp = SecsSince + 3600 * abs(GetTimeZoneOffset()+1)
End Function

Function GetTimeZoneOffset()
    Const sComputer = "."

    Dim oWmiService : Set oWmiService = _
        GetObject("winmgmts:{impersonationLevel=impersonate}!\\" _
                  & sComputer & "\root\cimv2")

    Dim cTimeZone : Set cTimeZone = _
        oWmiService.ExecQuery("Select * from Win32_TimeZone")

    Dim oTimeZone
    For Each oTimeZone in cTimeZone
        GetTimeZoneOffset = oTimeZone.Bias / 60
        Exit For
    Next
End Function
'********End Epoch Time Compute Section********

'****************Query Section*****************
Function GetBuffer(StrSrv) 
      Dim objWMIService, Item, Proc, Time
    
      strQuery = "select * from Win32_PerfFormattedData_MSSQLSERVER_SQLServerBufferManager"
   
      Set objWMIService = GetObject("winmgmts:\\" & StrSrv & "\root\cimv2")
      Set Item = objWMIService.ExecQuery(strQuery,,48)
	  Time = CStr(TimeStamp())
	  
     For Each Proc In Item
		 objFile.WriteLine "Freeliststalls/Second " & Proc.FreeliststallsPersec & " " & Time
		 objFile.WriteLine "Lazywrites/Second " & Proc.LazywritesPersec & " " & Time
		 objFile.WriteLine "Checkpointpages/Second " & Proc.CheckpointpagesPersec & " " & Time
		 objFile.WriteLine "Pagelifeexpectancy " & Proc.Pagelifeexpectancy & " " & Time
		 objFile.WriteLine "Pagelookups/Second " & Proc.PagelookupsPersec & " " & Time
		 objFile.WriteLine "Pagereads/Second " & Proc.PagereadsPersec  & " " & Time
		 objFile.WriteLine "Readaheadpages/Second " & Proc.ReadaheadpagesPersec & " " & Time
		 objFile.WriteLine "Databasepages " & Proc.Databasepages & " " & Time
		 objFile.WriteLine "Targetpages " & Proc.Targetpages & " " & Time
    Next
End Function
'**************End Query Section***************