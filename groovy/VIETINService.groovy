import com.misys.tools.integration.api.message.GMessage
import groovy.xml.StreamingMarkupBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.regex.Matcher
import java.util.regex.Pattern

final Logger logger = LoggerFactory.getLogger("VIETINService")

GMessage message = message
def xmlIn = message.messageBodyAsText
logger.info "xml in = ${xmlIn}"

def inputResult = []
def rootNode = new XmlSlurper().parseText(xmlIn)
rootNode.childNodes().toList().each {value -> inputResult.add(value.text())}

def FileName = inputResult.get(0).toString() + '.txt'
def InputMsg = inputResult.get(1).toString()
def greeting = ""

def currentDir = new File('').getAbsolutePath()
def createDir = currentDir + '/data/import/VIETIN/in'
def fail= currentDir + '/data/import/VIETIN/failed'

InputMsg = messageInput(InputMsg, logger)

if(InputMsg.trim().length() > 0){
	int a = writeFile(createDir, FileName ,InputMsg)
	logger.info "Create file Successfully "
}

Thread.sleep(2000);

def data = ''
if(InputMsg.trim().length() > 0){
	data= readFile(currentDir + '/data/queued-events/VietinMessageLog.csv')
}

logger.info "Vietin log Data: " + data 
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


def int writeFile(String Filepath, String Filename, String Content)
	{
		try{
		new File(Filepath,Filename).withWriter('utf-8') {
			writer -> writer.writeLine Content
			}
		}catch(Exception ex){
			logger.error 'Write file Error' + ex.toString()
			sendMailNotify(0, "ERROR write file VIETIN: ", ex.toString())
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

def String messageInput(String data, Logger logger){
	
	String FIN_AllContent = new String(data)
	String msg =""	
	
	try{
		String finF79 = FIN_AllContent.substring(FIN_AllContent.indexOf(":79:")+4, FIN_AllContent.indexOf("\n-}"))
		finF79 = finF79.replaceAll("\\]\\[", " = ")
		int numberOfOpenBlanket = finF79.replaceAll("\\]", "").length()
		int numberOfCloseBlanket = finF79.replaceAll("\\[", "").length()
		int indexOfFirstOpenBlanket = finF79.indexOf("[")
		List ls = ['110', '003', '007', '012', '019', '021', '022', '025', '026', '027', '028', '029', '030', '031', '032', '033', '034', '036', '037', '178', '180', '182']
	
		logger.info "data INT: " + numberOfOpenBlanket +" "+numberOfCloseBlanket+" "+indexOfFirstOpenBlanket 
	
		if ((numberOfOpenBlanket == numberOfCloseBlanket) && (numberOfOpenBlanket > 0) && (indexOfFirstOpenBlanket == 0)) {
			Matcher matcher = Pattern.compile(Pattern.quote("[") + "(.*?)" + Pattern.quote("]")).matcher(finF79)
			while (matcher.find()) {
				String f79Tag = matcher.group(1).substring(0,matcher.group(1).indexOf(":")).trim()
				String f79Value = matcher.group(1).substring( matcher.group(1).indexOf("=")+1).trim().replaceAll('\\.','')
			
				int id = ls.indexOf(f79Tag.trim())
				if(f79Tag=='027'){
					f79Value = f79Value + '00'
				}
				if(id >= 0){
					ls.set(id, f79Tag + f79Value)
				}
			}
		}
	
		for(String item : ls){
			msg = msg + "#" +  item
		}
		msg = msg + "#"
		logger.info "Data InputMsg: "+ msg
	}catch(Exception ex){
		logger.error 'Get data error: ' + ex.toString()
		sendMailNotify(1, "ERROR format điện VIETIN: ", ex.toString())
		return msg
	}
	return msg
	
}

def void sendMailNotify(def message_type, def subjectsuffix, def logger){
	def proc = Runtime.getRuntime().exec(
		"/data/kplusaddon/FFC/config/File2Kondor/sendMail.sh $message_type $subjectsuffix $logger")
}