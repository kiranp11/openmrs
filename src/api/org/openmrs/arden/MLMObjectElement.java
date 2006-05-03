package org.openmrs.arden;


import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Iterator;
import org.openmrs.Concept;
import org.openmrs.ConceptWord;
import org.openmrs.Obs;
import org.openmrs.api.ConceptService;
import org.openmrs.api.ObsService;
import org.openmrs.Patient;






public class MLMObjectElement implements ArdenBaseTreeParserTokenTypes {

	private boolean dbAccessRequired;
	private String conceptName;
	private String type;    // Exist, Last, First etc
	private String duration; // TODO
	private Concept conceptObj;	
	private Obs obsObj;
	private boolean isObsAvailable;
	private ConceptService cs;
	private ObsService os;
	private boolean evaluated;
	private boolean isEvaluated;
	private String answer;
	private Integer answerInt;
	private Integer compOp;
	private boolean hasConclude;
	private boolean concludeVal;
	private HashMap<String, String> userVarMap ;
	
	public MLMObjectElement(String s, String t, String d) {
		conceptName = s;
		isObsAvailable = false;
		type = t;
		duration = d;
		evaluated = false;
		isEvaluated = false;
		userVarMap = new HashMap <String, String>();
		dbAccessRequired = true;  // by default assume that we have to make an API call to get data
	}
	
	private void setObs(Obs o) {
		obsObj = o;
		isObsAvailable = true;
	}
	
	public void setServicesContext(ConceptService conceptService, ObsService obsService){
		cs = conceptService;
		os = obsService;	
	}
	
	private void setConcept(Concept c){
		conceptObj = c;
	}
	
	public void setAnswer (String s){
		answer = s;
	}
	public void setAnswer (Integer i){
		answerInt = i;
	}
	public void setCompOp (Integer op){
		compOp = op;
	}
	public void setConcludeVal (boolean val) {
		hasConclude = true;
		concludeVal = val;
	}
	
	public void addUserVarVal(String var, String val) {
		if(!userVarMap.containsKey(var)) {
			userVarMap.put(var, val);
		}
		else
		{
			//TODO either an error or overwrite previous one
		}
		
	}
	
	public boolean getConceptForPatient(Locale locale, Patient patient ) {
		
		boolean retVal = false;
		String  cn;
		int index;
		Concept concept;
		Set <Obs> MyObs ;
		Obs obs;
		List <ConceptWord>  conceptsWords;
		
		
//		 TODO: Need a better method to find a concept
		
		index = conceptName.indexOf("from");	// First substring
		cn = conceptName.substring(1,index);
		conceptsWords = cs.findConcepts(cn, locale, false);  
		if (!conceptsWords.isEmpty()) {
			    ConceptWord conceptWord = conceptsWords.get(0);
			    concept = conceptWord.getConcept(); 
			    setConcept(concept);
			    // Now get observations
			    MyObs = os.getObservations(patient, conceptObj);
				Iterator iter = MyObs.iterator();
				while(iter.hasNext())	{ // For now get the first
				  obs = (Obs) iter.next();
			 	  setObs(obs);		      
			      System.out.println(obsObj.getValueAsString(locale));
			      retVal = true;
				}
			}		
		return retVal;
	}
	
  
   public String getObsVal(Locale locale){
	   String val = null;
	   if(isObsAvailable){
		   val = obsObj.getValueAsString(locale);
	   }
	   return val;
   }
   
   public boolean evaluate(){
	   boolean retVal = false;
	   if (isEvaluated){
	      retVal = evaluated;
	   }
	   else {
	   switch(compOp) {
	   		case EQUALS:
	   			retVal = evaluateEquals(true);
	   			break;
	   		default:
	   			break;
	   	}
	   
	   }
	  return retVal;
   }
   public boolean evaluateEquals(boolean RHS) {
	   boolean retVal = false;
	   
	   if(isObsAvailable && RHS == true){
		   retVal = true;
	   }
	   else if (isObsAvailable && RHS == false) {
	   	   retVal = true;
	   }
	   evaluated = retVal;
	   isEvaluated = true;
	   return retVal;
   }
   
   public boolean evaluateEquals(String RHS) {
	   boolean retVal = false;
	   
	   if(isObsAvailable){
		   String val = obsObj.getValueText();
		   if(val.equals(RHS)){
			   retVal = true;  
		   }
	   }
	   else {
	   	   retVal = false;
	   }
	   evaluated = retVal;
	   isEvaluated = true;
	   return retVal;
   }
	
   public boolean getEvaluated(){
	      return evaluated;
   }

   public boolean isElementEvaluated(){
	   return isEvaluated;
   }
	public String getConceptName(){
		
		return conceptName;
	}
	
	public String getAnswer() {
		String retVal = "";
		if(answerInt != null)
			retVal = Integer.toString(answerInt);
		else
			retVal = answer;
		return retVal;
	}
	
	public String getCompOp(){
		String s =  Integer.toString(compOp);
		System.err.println(s);
		return s;
	}
	
	public String getConcludeVal(){
		String retVal;
		if(hasConclude){
			if(concludeVal == true) 
				retVal="true"; 
			else
				retVal="false";
		}
		else {
			retVal = "unknown";
		}
		return retVal;
	}
	
	public String getUserVarVal() {
		String s = "";
		if(!userVarMap.isEmpty()) {
			Set<String> keys = userVarMap.keySet();
			for(String key : keys) {
			     s += key + " = " + userVarMap.get(key)+ "\n";
			}
		}
		return s;
	}
	
	public void setDBAccessRequired(boolean val){
		dbAccessRequired = val;
	}
	
	public boolean getDBAccessRequired(){
		return dbAccessRequired;
	}
}
