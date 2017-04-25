/**
A. Pierce Matthews
04/24/17
CSCE-311 Final Project
*/

package osp.Ports;

import java.util.*;
import osp.IFLModules.*;
import osp.Threads.*;
import osp.Tasks.*;
import osp.Memory.*;
import osp.Utilities.*;

/**
   The studends module for dealing with ports. The methods 
   that have to be implemented are do_create(), 
   do_destroy(), do_send(Message msg), do_receive(). 


   @OSPProject Ports
*/

public class PortCB extends IflPortCB
{
    //IO buffer
    private int outBuff;
    private int inBuff;
    /**
       Creates a new port. This constructor must have

	   super();

       as its first statement.

       @OSPProject Ports
    */
    public PortCB()
    {
        // your code goes here
        super();
    }

    /**
       This method is called once at the beginning of the
       simulation. Can be used to initialize static variables.

       @OSPProject Ports
    */
    public static void init()
    {
        // your code goes here
        System.out.println("PortCB init called");
    }

    /** 
        Sets the properties of a new port, passed as an argument. 
        Creates new message buffer, sets up the owner and adds the port to 
        the task's port list. The owner is not allowed to have more 
        than the maximum number of ports, MaxPortsPerTask.

        @OSPProject Ports
    */
    public static PortCB do_create()
    {
        // your code goes here

        //Create new port
        PortCB newPort = new PortCB();

        TaskCB thisTask = null;
        try
        {
            thisTask = MMU.getPTBR().getTask();
        } 
        catch (NullPointerException e) 
        {
            System.out.println("Exeption caught, PortCB line 74" + e)
        }

        int portNum = thisTask.getPortCount();

        //Checks if adding this ports exceeds max
        if(portNum == MaxPortsPerTask)
        {
            System.out.println("Too many ports");
            return null;
        }

        if(thisTask.addPort(newPort) == FAILURE)
        {
            System.out.println("Port cannot be added");
            return null;
        }

        //initialize the stuff for the new port
        newPort.setTask(thisTask);
        newPort.setStatus(PortLive);
        newPort.inBuff = 0;
        newPort.outBuff = 0;

        //return the new port
        return newPort;

    }

    /** Destroys the specified port, and unblocks all threads suspended 
        on this port. Delete all messages. Removes the port from 
        the owners port list.
        @OSPProject Ports
    */
    public void do_destroy()
    {
        // your code goes here
        this.setStatus(PortDestroyed);
        this.notifyThreads();
        this.getTask().removePort(this);
        this.setTask(null);
    }

    /**
       Sends the message to the specified port. If the message doesn't fit,
       keep suspending the current thread until the message fits, or the
       port is killed. If the message fits, add it to the buffer. If 
       receiving threads are blocked on this port, resume them all.

       @param msg the message to send.

       @OSPProject Ports
    */
    public int do_send(Message msg)
    {
        // your code goes here
        //Is the message well formed
        if(msg == null || (PortBufferLength < msg.getLength()))
        {
            System.out.println("Message is invalid");
            return FAILURE;
        }

        SystemEvent newEvent = new SystemEvent("send_msg");
        TaskCB thisTask = null;
        ThreadCB thisThread = null;

        //Get the current task
        try
        {
            thisTask = MMU.getPTBR().getTask();
            thisThread = thisTask.getCurrentThread();
        }
        catch(NullPointerException e)
        {
            System.out.println("Null pointer, PortCB line 148");
        }

        thisThread.suspend(newEvent);
        int bufferRoom;

        boolean suspendMsg = true;
        //If the targetted thread is too full, the thread is suspended
        while(suspendMsg)
        {
            if(this.inBuff == this.outBuff)
            {
                if(this.isEmpty())
                {
                    bufferRoom = PortBufferLength;
                }
                else
                {
                    bufferRoom = 0;
                }
            }
            else
            {
                bufferRoom = PortBufferLength + this.outBuff - this.inBuff;
            }

            if(msg.getLength() <= bufferRoom)
            {
                suspendMsg = false;
            }
            else
            {
                thisThread.suspend(this);
            }

            if(thisThread.getStatus() == ThreadKill)
            {
                System.out.println("Current thread Killed");
                this.removeThread(thisThread);
                return FAILURE;
            }

            if(this.getStatus() != PortLive)
            {
                newEvent.notifyThreads();
                return FAILURE;
            }
        }

        this.appendMessage(msg);
        this.notifyThreads();
        this.inBuff = (this.inBuff + msg.getLength()) % PortBufferLength;
        newEvent.notifyThreads();
        System.out.println("Message sent");
        return SUCCESS;
    }

    /** Receive a message from the port. Only the owner is allowed to do this.
        If there is no message in the buffer, keep suspending the current 
	thread until there is a message, or the port is killed. If there
	is a message in the buffer, remove it from the buffer. If 
	sending threads are blocked on this port, resume them all.
	Returning null means FAILURE.

        @OSPProject Ports
    */
    public Message do_receive() 
    {
        // your code goes here
        TaskCB thisTask = null;
        ThreadCB thisThread = null;

        try 
        {
            thisTask = MMU.getPTBR().getTask();                                            
            thisThread = thisTask.getCurrentThread();
        }
        catch (NullPointerException e)
        {
            System.out.println("Exeption PortCB line 227");        
        }
        
        //If the task isnt current
        if(this.getTask() != thisTask)
        {
            return null;
        }       
    
        SystemEvent newEvent = new SystemEvent("receive_msg");
        thisThread.suspend(newEvent);
        boolean suspendMsg = true;

        //suspend
        while(suspendMsg)
        {
            if(this.isEmpty())
            {
                thisThread.suspend(this);
            }
            else
            {
                suspendMsg = false;
            }

            if( thisThread.getStatus() == ThreadKill)
            {
                this.removeThread(thisThread);
                newEvent.notifyThreads();
                return null;
            }

            if( this.getStatus() != PortLive)
            {
                newEvent.notifyThreads();
                return null;
            }   
        }
        
        Message currentMsg = this.removeMessage();
        this.outBuff = (this.outBuff + currentMsg.getLength()) % PortBufferLength; 
        this.notifyThreads();
        newEvent.notifyThreads();
        System.out.println("Message Recieved "+currentMsg);
        return currentMsg; 
    }

    /** Called by OSP after printing an error message. The student can
	insert code here to print various tables and data structures
	in their state just after the error happened.  The body can be
	left empty, if this feature is not used.
	
	@OSPProject Ports
    */
    public static void atError()
    {
        // your code goes here

    }

    /** Called by OSP after printing a warning message. The student
	can insert code here to print various tables and data
	structures in their state just after the warning happened.
	The body can be left empty, if this feature is not used.
     
	@OSPProject Ports
    */
    public static void atWarning()
    {
        // your code goes here

    }


    /*
       Feel free to add methods/fields to improve the readability of your code
    */

}

/*
      Feel free to add local classes to improve the readability of your code
*/
