package dk.thoerup.ircdoorman;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import org.pircbotx.Configuration;
import org.pircbotx.PircBotX;
import org.pircbotx.UtilSSLSocketFactory;
import org.pircbotx.exception.IrcException;

import com.google.common.base.Splitter;

public class DoormanMain {
	
	static Logger logger = Logger.getLogger( DoormanMain.class.getName() );

	public static void main(String args[]) throws IOException, IrcException {
		
		
		Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(new File("./doorman.ini"))) {
            p.load(in);
        }
		
        
        String host = p.getProperty("host", "");
        int port = Integer.parseInt( p.getProperty("port", "6697") );
        String serverPassword = p.getProperty("serverpassword", "");
        boolean useSsl = Boolean.parseBoolean(  p.getProperty("usessl", "true"));
        
        String strChannels = p.getProperty("channels", ""); 
        List<String> channels = Splitter.on(",").trimResults().omitEmptyStrings().splitToList(strChannels);
        
		
		Configuration.Builder configBuilder = new org.pircbotx.Configuration.Builder()
				.setAutoReconnectDelay(25)
				.setAutoReconnect(true)
				.setAutoReconnectAttempts(9999)
				
				.setName("DoormanBot") //Set the nick of the bot.
				.setAutoNickChange(true)				
				.setLogin("DoormanBot")
				.setRealName("hoerup's doormanbot")
				.setServerPassword(serverPassword)
				.addServer(host, port)
				.setAutoNickChange(true)								
				.addAutoJoinChannels(channels)				
				;
		if (useSsl) {
			configBuilder.setSocketFactory(new UtilSSLSocketFactory().trustAllCertificates());
		}
		
		LoginValidator staticLogin = new LoginValidator() {
			@Override
			public boolean login(String username, String password)  {
				return username.equals("test") && password.equals("test");
			}			
		};
		
		configBuilder.getListenerManager().addListener(  new DoormanListener(staticLogin, channels) );
		

		
		Configuration config = configBuilder.buildConfiguration();
		
		logger.info("Starting bot");
		try ( PircBotX bot = new PircBotX(config)) {
			bot.startBot();	
		};		
							
	}
}
