package dk.thoerup.ircdoorman.login;

import java.util.Properties;

public class LoginValidatorFactory {
	public static LoginValidator buildLoginValidator(Properties settings) {
		
		String method = settings.getProperty("loginmethod", "");
		
		switch(method) {
		case "static":
			return new StaticLoginValidator(settings);
			
		case "ldap":
			return new LdapLoginValidator(settings);
			
		default:
			throw new RuntimeException("unknown method: " + method);
			
		}
		
		
	}
}
