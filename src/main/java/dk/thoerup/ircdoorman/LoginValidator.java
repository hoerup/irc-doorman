package dk.thoerup.ircdoorman;

public interface LoginValidator {
	boolean login(String username, String password);
}
