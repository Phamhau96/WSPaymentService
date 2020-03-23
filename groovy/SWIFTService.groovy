import com.misys.tools.integration.api.message.GMessage
import groovy.xml.StreamingMarkupBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory


final Logger logger = LoggerFactory.getLogger("SWIFTService")

GMessage message = message
def xmlIn = message.messageBodyAsText
logger.info "xml in = ${xmlIn}"

def inputResult = []
def rootNode = new XmlSlurper().parseText(xmlIn)
rootNode.childNodes().toList().each {value -> inputResult.add(value.text())}

def FileName = inputResult.get(0).toString() + ".rje"
def InputMsg = inputResult.get(1).toString()
def swiftdata = ''
InputMsg.eachLine{
	line -> swiftdata += line + '\r\n'
}

def greeting = ""

def currentDir = new File('').getAbsolutePath()
def createDir = currentDir + '/data/import/SWIFT/in'
def fail= currentDir + '/data/import/SWIFT/failed'

int a = writeFile(createDir,FileName ,swiftdata, logger)

logger.info "Create file Successfully" 
Thread.sleep(2000);

def data = readFile(currentDir + '/data/queued-events/SwiftMessageLog.csv')
logger.info "Swift log Data: " + data 
	if(data.indexOf("SENT") >=0 ){
		greeting =  'Send Successfully'
	}else{
		greeting =  'Send Faild'
	}

def builder = new StreamingMarkupBuilder()
def xmlOut = builder.bind{
		mkp.declareNamespace('':'PaymentsWS.wsdl')
		'PaymentsMsgReply' (greeting)
		}.toString()
logger.info "response created = ${xmlOut}"


return message
        .result()
        .successful()
        .messageBody(xmlOut)
        .build()


def int writeFile(String Filepath, String Filename, String Content, Logger logger)
	{
		try{
			new File(Filepath,Filename).withWriter('UTF-8') {
				writer -> writer.writeLine Content
			}
		}catch(Exception ex){
			logger.error 'Write file error:' + ex.toString()
			sendMailNotify(1, "ERROR format dien SWIFT: ", ex.toString())
			return 1
		}
		return 0
	}

def String readFile(String Filepath){
	def data = ""
	try{
		new File(Filepath).eachLine {
			line -> data += line + " "
 		}			
	}catch(Exception ex){
		return ""
	}
	return data 
}

def void sendMailNotify(def message_type, def subjectsuffix, def logger){
	def proc = Runtime.getRuntime().exec(
		"/data/kplusaddon/FFC/config/File2Kondor/sendMail.sh $message_type $subjectsuffix $logger")
}