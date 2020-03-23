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

def FileName = inputResult.get(0).toString()
def InputMsg = inputResult.get(1).toString()
def greeting = ""

def currentDir = new File('').getAbsolutePath()
def createDir = currentDir + '/data/import/VCB/in'
def fail= currentDir + '/data/import/VCB/failed'

InputMsg = subString(InputMsg).trim()

int a = writeFile(createDir, FileName, InputMsg)

logger.info "Create file ok " + a
Thread.sleep(2000);

def data = readFile(currentDir + '/data/queued-events/VcbMessageLog.csv')
logger.info "Vcb log Data: " + data 
	if(data.indexOf("SENT") >=0 ){
		greeting =  'Send Successfully'
	}else{
		greeting =  'Send Fail'
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


def int writeFile(String Filepath, String Filename, String Content)
	{
		try{
			new File(Filepath,Filename).withWriter('utf-8') {
				writer -> writer.writeLine Content
			}
		}catch(Exception ex){
			logger.error 'Write file Error' + ex.toString()
			sendMailNotify(1, "ERROR write file VCB: ", ex.toString())
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

def String subString(String data){
	try{
		def str =  data.substring(data.indexOf("{4:") + 3, data.indexOf("-}"))
	}catch(Exception ex){
		sendMailNotify(1, "ERROR format dien VCB: ", ex.toString())
	}
	return str
}

def void sendMailNotify(def message_type, def subjectsuffix, def logger){
	def proc = Runtime.getRuntime().exec(
		"/data/kplusaddon/FFC/config/File2Kondor/sendMail.sh $message_type $subjectsuffix $logger")
}