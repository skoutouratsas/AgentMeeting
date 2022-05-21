package jadelab2;

import jade.core.Agent;
import jade.core.AID;
import jade.core.behaviours.*;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.domain.DFService;
import jade.domain.FIPAException;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import java.util.ArrayList;
import java.util.Iterator;
import jade.lang.acl.StringACLCodec;
import java.io.StringReader;

import java.util.*;

public class MeetingAgent extends Agent {
	
  static final int HOURS = 5;
	
  private double[] calendar;
  
  private int max_index=-1;
  
  private AID[] others;
  
  private double[] sumOfPreferences;
  
	protected void setup() {
	  System.out.println("Hello! " + getAID().getLocalName() + " is ready for scheduling the meeting.");
	  
	  
		calendar = new double[HOURS];
		sumOfPreferences = new double[HOURS];
		for (int i = 0; i < HOURS; ++i){
			calendar[i]= Math.random();
			sumOfPreferences[i] = calendar[i];
		}
		  
		  
		for (int i = 0; i < HOURS; ++i)	  {
			if(Math.random()>0.8)
				calendar[i]= -1;
				sumOfPreferences[i] = calendar[i];
		}
		
	  
		//book selling service registration at DF
		DFAgentDescription dfd = new DFAgentDescription();
		dfd.setName(getAID());
		ServiceDescription sd = new ServiceDescription();
		sd.setType("meeting");
		sd.setName("Meeting");
		dfd.addServices(sd);
		
		try {
		  DFService.register(this, dfd);
		}
		catch (FIPAException fe) {
		  fe.printStackTrace();
		}

		addBehaviour(new FindOthers());
		addBehaviour(new ReplyAboutPreference());
		addBehaviour(new ReplyAboutProposal());
	
  }

	protected void takeDown() {
		System.out.println("Meeting agent " + getAID().getLocalName() + " terminated.");
	}
	
	private class FindOthers extends CyclicBehaviour {
		
		private int step = 0;
		private int repliesCnt = 0;
		
		private double slot_worth=0;
		private MessageTemplate mt;
		private ACLMessage cfp;
		private ACLMessage reply;
		private long round;
		public void action()
		{
			switch (step) {
			case 0://find other agents
				//search only if the purchase task was ordered
				round = System.currentTimeMillis();

				System.out.println(getAID().getLocalName() + ": Searching for others...");
				//update a list of known agents
				DFAgentDescription template = new DFAgentDescription();
				ServiceDescription sd = new ServiceDescription();
				sd.setType("meeting");
				template.addServices(sd);
				try
				{
					DFAgentDescription[] result = DFService.search(myAgent, template);
					//System.out.println(getAID().getLocalName() + ": the following agents have been found");
					others = new AID[result.length];
					for (int i = 0; i < result.length; ++i)
					{
						others[i] = result[i].getName();
						//System.out.println(others[i].getLocalName());
					}
					System.out.println("I am :" +getAID().getLocalName() );
					for (int i = 0; i < HOURS; ++i)
					{
						if(calendar[i]!=-1)
							System.out.printf("|%.2f|", calendar[i]);
						else
							System.out.printf("|X.XX|", calendar[i]);
						

						
					}
					System.out.println("");
				}
				catch (FIPAException fe)
				{
					fe.printStackTrace();
				}
				step = 1;
				break;
				
				
			case 1://find my most preferred and ask if others like it
			
				for (int i = 0; i < HOURS; ++i)
				{
					sumOfPreferences[i] = calendar[i];
					cfp = new ACLMessage(ACLMessage.CFP);
					for (int j = 0; j < others.length; ++j) {
						if(!others[j].equals(getAID()))
							cfp.addReceiver(others[j]);
						} 
						cfp.setContent(i+"");
						cfp.setConversationId("availability");
						cfp.setReplyWith(""+round);
						myAgent.send(cfp);
				}
				
				 mt = MessageTemplate.and(MessageTemplate.MatchConversationId("availability-response"),
	                               MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
					
				
				
				step = 2;
				break;
				
				
			case 2: //get responses... If someone is unavailable find next most preferred. Else compute the value of time slot
				
				
				reply = myAgent.receive(mt);
				if (reply != null) {
					if (reply.getPerformative() == ACLMessage.INFORM) {
						String[] answer = reply.getContent().split(":"); 
						int idx = Integer.parseInt(answer[0]);
						double pref=Double.parseDouble(answer[1]);
						//split
						if(pref == -1){
							sumOfPreferences[idx] = -1;
						}
						else{
							if(sumOfPreferences[idx] != -1)
								sumOfPreferences[idx]+= pref;
						}
					}
					repliesCnt++;
					if (repliesCnt  >= (others.length-1)*HOURS) {
						//all proposals have been received
						repliesCnt = 0;
						step = 3; 
						break;
					}
					

				}
				else{
					block();
				}
				break;
			case 3: //find which slot is the most preferrable
				double max=-1.0;
				max_index=-1;
				for (int i = 0; i < HOURS; ++i)
				{
					if(sumOfPreferences[i] == -1)
						continue;
					if(sumOfPreferences[i]>max){
						max= sumOfPreferences[i];
						max_index=i;
					}
				}
				if(max == -1){
					//CANCEL MEETING
					step = 6;
					break;
				}
				 //System.out.println(getAID().getLocalName()+"THe largest ifound at"+ +max_index +"value is: "+ sumOfPreferences[max_index]);
				
				
				
				
				ACLMessage msg = new ACLMessage(ACLMessage.PROPOSE);
				for (int j = 0; j < others.length; ++j) {
					if(!others[j].equals(getAID()))
						msg.addReceiver(others[j]);
				} 
				msg.setContent(max_index+"");
				msg.setConversationId("time-proposal");
				msg.setReplyWith(""+round);
				myAgent.send(msg);
				
			
				mt = MessageTemplate.and(MessageTemplate.MatchConversationId("proposal-result"),
	                               MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));

				
				
				step = 4;
				
				break;
				
			case 4:
				reply = myAgent.receive(mt);
				if (reply != null) {
						
					if (reply.getPerformative() == ACLMessage.ACCEPT_PROPOSAL) {
						//System.out.println(getAID().getLocalName()	+" I got  " + repliesCnt);
						repliesCnt++;
						if (repliesCnt  >= (others.length-1)) {
							repliesCnt = 0;
							//all proposals have been received
							step = 5;
							break ;
							
						}
					}
					else if (reply.getPerformative() == ACLMessage.REFUSE) {
						
						msg = new ACLMessage(ACLMessage.PROPOSE);
						msg.addReceiver(reply.getSender());
						msg.setContent(max_index+"");
						msg.setReplyWith(""+round);
						msg.setConversationId("time-proposal");
						myAgent.send(msg);
						
					}
					if (reply.getPerformative() == ACLMessage.REJECT_PROPOSAL) {
							
						System.out.println(getAID().getLocalName()	+" The meeting cannot happen " );
						max_index = -1;
						for (int i = 0; i < HOURS; ++i){
							sumOfPreferences[i] = calendar[i];
						}
						while(true){}
						
					}

				}
				else{
					block();
				}
				break;
			case 5:
				if(calendar[max_index]!=-1){
					System.out.println(getAID().getLocalName()	+" The meeting is happening at: " + max_index);
					
					calendar[max_index] = -1;
					for (int i = 0; i < HOURS; ++i){
						sumOfPreferences[i] = calendar[i];
					}
					
					step = 0;
					max_index=-1;
					
					try{
						Thread.sleep(7000);
					}
					
					catch(InterruptedException e){}
					break;
				}
			case 6:// the meeting will not happen.. inform other
				
				msg = new ACLMessage(ACLMessage.REJECT_PROPOSAL);
				for (int j = 0; j < others.length; ++j) {
					if(!others[j].equals(getAID()))
						msg.addReceiver(others[j]);
				} 
				msg.setContent(max_index+"");
				msg.setConversationId("proposal-result");
				msg.setReplyWith(""+round);
				myAgent.send(msg);
				
				System.out.println(getAID().getLocalName()	+" The meeting cannot happen " );
				max_index = -1;
				for (int i = 0; i < HOURS; ++i){
					sumOfPreferences[i] = calendar[i];
				}
				while(true){}
				
			  
				
			}  
		}
		  
	

	}
	
	private class ReplyAboutPreference extends CyclicBehaviour {
		public void action() {
			
			MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchConversationId("availability"),MessageTemplate.MatchPerformative(ACLMessage.CFP));
			ACLMessage msg = myAgent.receive(template);
			if (msg != null) {
			  int index = Integer.parseInt(msg.getContent());
			  ACLMessage reply = msg.createReply();
			  double answer = calendar[index];
			  //System.out.println(getAID().getLocalName()	+" MY ANSWER IS " + answer);
			  reply.setConversationId("availability-response");
			  reply.setPerformative(ACLMessage.INFORM);
			  reply.setContent(index+":"+answer);
			  myAgent.send(reply);
			}
			else {
			  block();
			}
		}
		
	}
	
	private class ReplyAboutProposal extends CyclicBehaviour {
		public void action() {
			
			MessageTemplate template = MessageTemplate.and(MessageTemplate.MatchConversationId("time-proposal"),MessageTemplate.MatchPerformative(ACLMessage.PROPOSE));
			ACLMessage msg = myAgent.receive(template);
			if (msg != null) {
			  int index = Integer.parseInt(msg.getContent());
			  ACLMessage reply = msg.createReply();
			  
			  
			  if(index == max_index ){
			  //System.out.println(getAID().getLocalName()+"got:"+ index + ",,," +max_index +"value is: "+ sumOfPreferences[max_index]);
	
				reply.setConversationId("proposal-result");
				reply.setPerformative(ACLMessage.ACCEPT_PROPOSAL);
				reply.setContent("");
				//System.out.println(getAID().getLocalName()	+" I AGREE" );
				
				
				
				myAgent.send(reply);
			  }
			else if (max_index == -1){
				reply.setConversationId("proposal-result");
				reply.setPerformative(ACLMessage.REFUSE);
				reply.setContent("");
				myAgent.send(reply);
			  }
			  
			  
			}
			else {
			  block();
			}
		}
		
	}
	
	
}
