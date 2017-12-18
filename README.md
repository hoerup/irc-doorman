[![Build Status](https://travis-ci.org/hoerup/irc-doorman.svg?branch=master)](https://travis-ci.org/hoerup/irc-doorman)

# irc-doorman


## building

* You need a host with openjdk-8-jdk and gradle
* git clone https://github.com/hoerup/irc-doorman.git
* cd irc-doorman
* gradle shadow

## running
* cp doorman.ini-sample doorman.ini
* edit doorman.ini and adjust to your settings
* start bot with: java -jar build/libs/irc-doorman-1.0-all.jar
