package dk.thoerup.ircdoorman.login;

import java.util.Properties;

public class StaticLoginValidator implements LoginValidator {

	String staticuser;
	String staticpass;
	
	public StaticLoginValidator(Properties settings) {
		staticuser = settings.getProperty("staticuser", "test");
		staticpass = settings.getProperty("staticpass", "test");
	}

	@Override
	public boolean login(String username, String password)  {
		return username.equals(staticuser) && password.equals(staticpass);
	}			
	
}
