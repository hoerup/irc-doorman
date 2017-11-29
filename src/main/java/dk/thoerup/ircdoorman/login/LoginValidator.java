package dk.thoerup.ircdoorman.login;

public interface LoginValidator {
	boolean login(String username, String password);
}
