package com.epam.reportportal.cucumber.integration.service;

public class Belly {
	
	private int satiation = 0;
	
    public void eat(int cukes) {
    	satiation += cukes;
    }
    
    public void wait(int hours) {
    	if ((hours > 0) && (satiation > 0)) {
    		int utilized = 60 * hours;
    		if (utilized > satiation) {
    			utilized = satiation;
    		}
    		satiation -= utilized;
    	}
    }
    
    public boolean growl() {
    	return satiation <= 0;
    }
}
