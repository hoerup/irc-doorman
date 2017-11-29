package dk.thoerup.ircdoorman.login;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;
import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class LdapLoginValidator implements LoginValidator {
	
	Logger logger = Logger.getLogger(LdapLoginValidator.class.getName());
	
	private String url;
	private String domainName;


	public LdapLoginValidator(Properties settings) {
		url = settings.getProperty("ldapurl");
		domainName = settings.getProperty("ldapdomain");
	
	}

	@Override
	public boolean login(String username, String password) {
		try {
			return ldapAuthentication(url, username, domainName, password);
		} catch (Exception e) {
			logger.log(Level.INFO, "Auth failed", e);
			return false;
		}
	}

	// kindly provided by StackOverflow :)
	
	// https://stackoverflow.com/questions/12317205/ldap-authentication-using-java
	
	// url : ldap://1.2.3.4:389 or ldaps://1.2.3.4:636
	// principalname firstname.lastname@mydomain.com
	// domainName : mydomain.com or empty
	private boolean ldapAuthentication(String url, String principalName, String domainName, String password) throws NamingException{


		 

		if (domainName==null || "".equals(domainName)) {
			int delim = principalName.indexOf('@');
			domainName = principalName.substring(delim+1);
		}

		Properties props = new Properties();
		props.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory");
		props.put(Context.PROVIDER_URL, url); 
		props.put(Context.SECURITY_PRINCIPAL, principalName); 
		props.put(Context.SECURITY_CREDENTIALS, password); // secretpwd
		if (url.toUpperCase().startsWith("LDAPS://")) {
			props.put(Context.SECURITY_PROTOCOL, "ssl");
			props.put(Context.SECURITY_AUTHENTICATION, "simple");
			props.put("java.naming.ldap.factory.socket", SSLSocketFactory.class.getName());         
		}
		

		InitialDirContext context = new InitialDirContext(props);
		try {
			SearchControls ctrls = new SearchControls();
			ctrls.setSearchScope(SearchControls.SUBTREE_SCOPE);
			NamingEnumeration<SearchResult> results = context.search(toDC(domainName),"(& (userPrincipalName="+principalName+")(objectClass=user))", ctrls);
			if(!results.hasMore())
				throw new AuthenticationException("Principal name not found");

			SearchResult result = results.next();
			System.out.println("distinguisedName: " + result.getNameInNamespace() ); // CN=Firstname Lastname,OU=Mycity,DC=mydomain,DC=com

			Attribute memberOf = result.getAttributes().get("memberOf");
			if(memberOf!=null) {
				for(int idx=0; idx<memberOf.size(); idx++) {
					System.out.println("memberOf: " + memberOf.get(idx).toString() ); // CN=Mygroup,CN=Users,DC=mydomain,DC=com
					//Attribute att = context.getAttributes(memberOf.get(idx).toString(), new String[]{"CN"}).get("CN");
					//System.out.println( att.get().toString() ); //  CN part of groupname
				}
			}
		} finally {
			try { context.close(); } catch(Exception ex) { }
		}
		return true;
	}
	
    /**
     * Create "DC=sub,DC=mydomain,DC=com" string
     * @param domainName    sub.mydomain.com
     * @return
     */
    private static String toDC(String domainName) {
        StringBuilder buf = new StringBuilder();
        for (String token : domainName.split("\\.")) {
            if(token.length()==0) continue;
            if(buf.length()>0)  buf.append(",");
            buf.append("DC=").append(token);
        }
        return buf.toString();
    }


    public static class DummySSLSocketFactory extends SSLSocketFactory {
    	private SSLSocketFactory socketFactory;
    	public DummySSLSocketFactory() {
    		try {
    			SSLContext ctx = SSLContext.getInstance("TLS");
    			ctx.init(null, new TrustManager[]{ new DummyTrustManager()}, new SecureRandom());
    			socketFactory = ctx.getSocketFactory();
    		} catch ( Exception ex ){ throw new IllegalArgumentException(ex); }
    	}

    	public static SocketFactory getDefault() { return new DummySSLSocketFactory(); }

    	@Override public String[] getDefaultCipherSuites() { return socketFactory.getDefaultCipherSuites(); }
    	@Override public String[] getSupportedCipherSuites() { return socketFactory.getSupportedCipherSuites(); }

    	@Override public Socket createSocket(Socket socket, String string, int i, boolean bln) throws IOException {
    		return socketFactory.createSocket(socket, string, i, bln);
    	}
    	@Override public Socket createSocket(String string, int i) throws IOException, UnknownHostException {
    		return socketFactory.createSocket(string, i);
    	}
    	@Override public Socket createSocket(String string, int i, InetAddress ia, int i1) throws IOException, UnknownHostException {
    		return socketFactory.createSocket(string, i, ia, i1);
    	}
    	@Override public Socket createSocket(InetAddress ia, int i) throws IOException {
    		return socketFactory.createSocket(ia, i);
    	}
    	@Override public Socket createSocket(InetAddress ia, int i, InetAddress ia1, int i1) throws IOException {
    		return socketFactory.createSocket(ia, i, ia1, i1);
    	}
    }

	public static class DummyTrustManager implements X509TrustManager {
		@Override public void checkClientTrusted(X509Certificate[] xcs, String str) {
			// do nothing
		}
		@Override public void checkServerTrusted(X509Certificate[] xcs, String str) {
			/*System.out.println("checkServerTrusted for authType: " + str); // RSA
	        for(int idx=0; idx<xcs.length; idx++) {
	            X509Certificate cert = xcs[idx];
	            System.out.println("X500Principal: " + cert.getSubjectX500Principal().getName());
	        }*/
		}
		@Override public X509Certificate[] getAcceptedIssuers() {
			return new java.security.cert.X509Certificate[0];
		}
	}

}
