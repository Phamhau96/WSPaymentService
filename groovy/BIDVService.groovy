import com.misys.tools.integration.api.message.GMessage
import groovy.xml.StreamingMarkupBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.regex.Matcher
import java.util.regex.Pattern

final Logger logger = LoggerFactory.getLogger("BIDVService")

GMessage message = message
def xmlIn = message.messageBodyAsText
logger.info "xml in = ${xmlIn}"

def inputResult = []
def rootNode = new XmlSlurper().parseText(xmlIn)
rootNode.childNodes().toList().each {value -> inputResult.add(value.text())}

//def FileName = inputResult.get(0).toString() + '.txt'
def FileName = 'TTDP_IN'

SimpleDateFormat format = new SimpleDateFormat('ddMMyy')
Date date = new Date();
FileName += '_' + format.format(date)
format = new SimpleDateFormat('hhmmss')
FileName += '_9999' + format.format(date) + '.txt'

def InputMsg = inputResult.get(1).toString()
InputMsg = createBIDV(InputMsg, logger)


def greeting = ""

def currentDir = new File('').getAbsolutePath()
def createDir = currentDir + '/data/import/BIDV/in'
def fail= currentDir + '/data/import/BIDV/failed'

int a = writeFile(createDir,FileName ,InputMsg)

logger.info "Create file Successfully "
Thread.sleep(2000);

def data = readFile(currentDir + '/data/queued-events/BidvMessageLog.csv')
logger.info "Bidv log Data: " + data 
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
			logger.error 'Error' + ex.toString()
			sendMailNotify(1, "ERROR write file dien BIDV: ", ex.toString())
			return 1
		}
		return 0
	}

def String readFile(String Filepath){
	def data = ""
	try{
		new File(Filepath).eachLine {
			line -> data += line + "\n"
 		}			
	}catch(Exception ex){
		return ""
	}
	return data 
}

def String createBIDV(String data, Logger logger){
		
	def map = [:]
	map = getField(data, logger);
	String msg = ''
	for (int i = 1; i <= 18; i++){
		String value = map.get(i+'')
		if(value.equals('') || value == null){
			value = ''
		}
		if(i == 5){
			SimpleDateFormat format = new SimpleDateFormat('dd/MM/yyyy')
			Date date = format.parse(value)
			format = new SimpleDateFormat('yyMMdd')
			value = format.format(date)
			map.put('5', value)
		}
		
		if(i == 7){
			value = value.replace(',', '')
			map.put('7', value)
		}
		
		msg += value
	}
	
	String dataBIDV = addData(map, checksum(msg, logger))
	logger.info "BIDV Msg: " +dataBIDV
	return dataBIDV
		
}

def String addData(def map, def checksum){
	String data = '<rows>'
	data += '<row checksum="' + checksum +'" >'
	for(int i = 1; i <= 18; i++){
		String value = map.get(i+'')
		if(value.equals('') || value == null){
			value = ''
		}
		data += '<field name="F' + i +'" value="' + value +'" />'
	}
	data += '</row></rows>'
	
	return data
}

def Map getField(String data, Logger logger) {
	def map = [:]
	String FIN_AllContent = data
	try{
		String finF79 = FIN_AllContent.substring(FIN_AllContent.indexOf(":79:")+4, FIN_AllContent.indexOf("\n-}"))
		finF79 = finF79.replaceAll("\\]\\[", " = ")
		int numberOfOpenBlanket = finF79.replaceAll("\\]", "").length()
		int numberOfCloseBlanket = finF79.replaceAll("\\[", "").length()
		int indexOfFirstOpenBlanket = finF79.indexOf("[")
	
		if ((numberOfOpenBlanket == numberOfCloseBlanket) && (numberOfOpenBlanket > 0) && (indexOfFirstOpenBlanket == 0)) {
			Matcher matcher = Pattern.compile(Pattern.quote("[") + "(.*?)" + Pattern.quote("]")).matcher(finF79)
			while (matcher.find()) {
				String f79Tag = matcher.group(1).substring(0, matcher.group(1).indexOf(":")).trim()
				String f79Value = matcher.group(1).substring( matcher.group(1).indexOf("=")+1).trim()
				logger.info "Data: " + f79Tag + "  " + f79Value
				map.put(f79Tag, f79Value)
			}
		}
	}catch(Exception ex){
		sendMailNotify(1, "ERROR format dien BIDV: ", ex.toString())
	}

	return map
}
	
def String checksum(String msg, Logger logger){
	MessageDigest md
	String out = ''
	logger.info 'MessageDigest: ' + msg 
	try {
		md = MessageDigest.getInstance('SHA-512');
		md.update(msg.getBytes('UTF-8'));

		byte[] mb = md.digest();

		for (int i = 0; i < mb.length; i++) {
			byte temp = mb[i];
			String s = Integer.toHexString(new Byte(temp));
			while (s.length() < 2) {
				s = "0" + s;
			}
			s = s.substring(s.length() - 2);
			out += s;
		}
	} catch (Exception e) {
		logger.error e.message
		sendMailNotify(1, "ERROR ma hoa dien BIDV: ", e.message)
	} catch (UnsupportedEncodingException e){
		logger.error e.message
		sendMailNotify(1, "ERROR ma hoa dien BIDV: ", e.message)
	}

	return out
}

def void sendMailNotify(def message_type, def subjectsuffix, def logger){
	def proc = Runtime.getRuntime().exec(
		"/data/kplusaddon/FFC/config/File2Kondor/sendMail.sh $message_type $subjectsuffix $logger")
}
