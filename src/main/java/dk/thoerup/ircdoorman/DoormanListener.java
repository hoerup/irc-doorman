package dk.thoerup.ircdoorman;


import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.pircbotx.Channel;
import org.pircbotx.PircBotX;
import org.pircbotx.ReplyConstants;
import org.pircbotx.hooks.ListenerAdapter;
import org.pircbotx.hooks.events.JoinEvent;
import org.pircbotx.hooks.events.KickEvent;
import org.pircbotx.hooks.events.MotdEvent;
import org.pircbotx.hooks.events.PrivateMessageEvent;
import org.pircbotx.hooks.events.QuitEvent;
import org.pircbotx.hooks.events.ServerResponseEvent;

import com.google.common.base.Splitter;

import dk.thoerup.ircdoorman.login.LoginValidator;


public class DoormanListener extends ListenerAdapter {
	
	Logger logger = Logger.getLogger( DoormanListener.class.getName());
	
	static final int TIMETOLIVE = 2*60*60*1000;//Session ok for 2 hours
	
	Timer timer;
	
	Map<String,Long> approvedHostMasks = new ConcurrentHashMap<>();
	
	List<String> channels;
	LoginValidator loginValidator;

	String operuser;
	String operpass;
	
	public DoormanListener(LoginValidator loginValidator, List<String> channels, String operuser, String operpass) {
		this.channels = channels;
		this.loginValidator = loginValidator;
		this.operuser = operuser;
		this.operpass = operpass;
	}

	@Override
	public void onJoin(JoinEvent event) throws Exception {
		if (event.getBot().getUserBot().equals( event.getUser() )) {
			event.getChannel().send().setMode("+o", event.getBot().getNick() );
			return;
		}
		
		String mask = event.getUser().getHostmask();
		
		if ( ! approvedHostMasks.containsKey(mask)) {
			event.getChannel().send().kick(event.getUser(), "Please login to DoormanBot before joining");
			return;
		}
		
		long expiration = approvedHostMasks.get(mask);
		long timeLeft = expiration - System.currentTimeMillis();
		if ( timeLeft <= 0) {
			if ( Math.abs(timeLeft) <= 60*60*1000) { //expired less than 1 hour ago
				event.getChannel().send().kick(event.getUser(), "Session timed out - please login to DoormanBot before joining");
			} else {				
				event.getChannel().send().kick(event.getUser(), "Please login to DoormanBot before joining");
				approvedHostMasks.remove(mask);
			}
			return;
		}
		

	}
	
	@Override
	public void onMotd(MotdEvent event) {

		event.getBot().sendRaw().rawLine("OPER " + operuser + " " + operpass);
		
		if (timer == null) {
			timer = new Timer();
			timer.schedule( new PingTimerTask(event.getBot()), 60*1000L, 60*1000L);
		}
	}
	


	@Override
	public void onPrivateMessage(PrivateMessageEvent event) throws Exception {

		List<String> parts = Splitter.on(' ').omitEmptyStrings().trimResults().splitToList( event.getMessage() );
		
		String cmd = parts.get(0).toLowerCase();
		
		switch(cmd) {
		case "login":
			login(parts, event);
			break;
		case "logout":
			logout(event);
			break;
		case "status":	
			sendLoginStatus(event);
			break;
		default:
			event.respond("Unknown command: " + cmd);
			sendUsage(event);
			return;
		}
	}
	
	private void sendUsage(PrivateMessageEvent event) {
		event.respond("To login:" );
		event.respond("/msg DoormanBot login <username> <password>");
		event.respond("To logout:" );
		event.respond("/msg DoormanBot logout");
		event.respond("To query status:" );
		event.respond("/msg DoormanBot status");		
	}
	
	
	private void logout(PrivateMessageEvent event) {
		String mask = event.getUser().getHostmask();
		if ( approvedHostMasks.containsKey(mask)) {
			approvedHostMasks.remove(mask);
			event.respond("You are now logged out");
			
			for (Channel chan : event.getUser().getChannels() ) {
				if (  channels.contains( chan.getName() )) {
					chan.send().kick( event.getUser(), "You're logged out - please leave");
				}
			}
		} else {
			event.respond("You are not logged in");
		}
	}
	
	private void login(List<String> parts, PrivateMessageEvent event) {
		if (parts.size() != 3) {
			sendUsage(event);
			return;
		}
		
		String username = parts.get(1);
		String password = parts.get(2);
		
		
		if (loginValidator.login(username, password)) {
			String mask = event.getUser().getHostmask();
			long expire = System.currentTimeMillis() + TIMETOLIVE;
			approvedHostMasks.put(mask, expire);
			
			event.respond("Login ok");
		} else {
			event.respond("Login failed");
		}
	}
	
	private void sendLoginStatus(PrivateMessageEvent event) {
		String mask = event.getUser().getHostmask();
		if ( approvedHostMasks.containsKey(mask)) {
			long expiration = approvedHostMasks.get(mask);
			long timeLeft = expiration - System.currentTimeMillis();
			
			if (timeLeft > 0) {
				Duration d = Duration.ofMillis(timeLeft);
				event.respond("You are logged in, with " + d.toString() + "  left of your current session");
			} else {
				event.respond("Your session has exipired");
			}
			
		} else {
			event.respond("You are not logged in");
		}
	
	}

	

	@Override
	public void onQuit(QuitEvent event) throws Exception {
		if (event.getBot().getUserBot().equals( event.getUser() )) {
			return; //don't react on self
		}
		
		String mask = event.getUser().getHostmask();
		approvedHostMasks.remove(mask);
		
	}

	@Override
	public void onKick(KickEvent event) throws Exception {
		if (event.getBot().getNick().equals( event.getRecipient().getNick() )) {			
			event.getBot().sendIRC().joinChannel(  event.getChannel().getName() );
		}
	}
	
	@Override
	public void onServerResponse(ServerResponseEvent event) {
				
		if (event.getCode() == ReplyConstants.RPL_YOUREOPER) { // our oper credentials has been accepted
			logger.info("oper credentials accepted by server(RPL_YOUREOPER)");
		}
		
		if ( event.getCode() == ReplyConstants.ERR_NOOPERHOST) {			
			logger.severe( "Invalid oper config: " + event.getRawLine() );
			
			System.exit(0);
		}
	}
	

	public static class PingTimerTask extends TimerTask {
		
		private final PircBotX bot;
		
		public PingTimerTask(PircBotX bot) {
			this.bot = bot;
		}

		@Override
		public void run() {
			if (bot.isConnected()) {
				bot.sendRaw().rawLine("PING");
			}
			
		}
		
	}
	
}
