import qupath.ext.py4j.core.GatewayManager

/*
 * This script starts a gateway to enable Python and Java/QuPath to communicate
 * on the provided port and using the provided token.
 *
 * Once this script is run, the gateway will stay open until QuPath is closed.
 * This means that you can't run the script multiple times with the same port
 * without restarting QuPath first, as there can only be one gateway per port.
 */

def port = -1       // the port the gateway should use, or a negative number to use the default port
def token = ""       // the token the gateway should accept, or an empty text to disable authentication. You'll need to
                            // to copy this token to your Python program if it is not empty

def gatewayManager = new GatewayManager()
gatewayManager.start(port, token)

if (gatewayManager.isRunning().get()) {
    println "Gateway started"
} else {
    println "Gateway not started. See the logs for more information"
}