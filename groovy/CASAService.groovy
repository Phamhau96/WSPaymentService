import com.misys.tools.integration.api.message.GMessage
import groovy.xml.StreamingMarkupBuilder
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.regex.Matcher
import java.util.regex.Pattern
import wslite.soap.*
import com.misys.tools.integration.api.annotation.InjectResource
import com.misys.tools.integration.api.component.resource.properties.GPropertiesResource
import groovy.transform.Field
import java.util.Date
import groovy.sql.Sql
import java.sql.*

@Field
@InjectResource(required = true)
GPropertiesResource propertiesResource
final Logger logger = LoggerFactory.getLogger("CASAService")

GMessage message = message
def xmlIn = message.messageBodyAsText
logger.info "xml in = ${xmlIn}"

def inputResult = []
def rootNode = new XmlSlurper().parseText(xmlIn)
rootNode.childNodes().toList().each {value -> inputResult.add(value.text())}

def FileName = inputResult.get(0).toString()
def InputMsg = inputResult.get(1).toString()
def greeting = ""

def casaUrl = propertiesResource.getProperty('casaUrl')
def channel1 = propertiesResource.getProperty('casaServiceChannel')
def type1 = propertiesResource.getProperty('casaTransactionType')


def map = getField(InputMsg, logger)
logger.info "Data" + map

def mailHeader_Id = FileName.replaceAll('[^0-9]', '')
logger.info mailHeader_Id

def dbURL = propertiesResource.getProperty('dbKondor_URL')
def dbUsername = propertiesResource.getProperty('dbKondor_Username')
def dbPassword = propertiesResource.getProperty('dbKondor_Password')
def dbDriver = propertiesResource.getProperty('dbKondor_Driver')
def db 
def service1
logger.info dbURL + '  '+dbUsername + '  ' + dbPassword + '  ' + dbDriver
try{
	db = Sql.newInstance(dbURL,dbUsername,dbPassword,dbDriver)
}catch (Exception e){
	logger.error "Connect Error: " + e.getMessage()
}

	service1 = getPaymentClass(logger, db, map.get('RelatedRef'))
	

logger.info "Log RQ: " + getRequestSoap(map, service1, channel1, type1)

try {
		Date date = Date.parse( 'dd/MM/yyyy', map.get('ValueDate') )
		//def valueDate = date.format('yyyyMMdd')
		def valueDate = '20191223'
		
		def client = new SOAPClient(casaUrl)
		def response = client.send(connectTimeout:20000,
									readTimeout:50000,
									useCaches:false,
									followRedirects:false,
									sslTrustAllCerts:false){
		envelopeAttributes "xmlns:asb":"http://www.vpbank.com.vn/ASBOTreasuryOut", "xmlns:xsi":"http://www.w3.org/2001/XMLSchema-instance"
		body {
			"asb:makeFundTransferRq" ("sessionId":"TTS" + map.get('RelatedRef') , "xsi:type":"asb:MakeCACATransferRqType"){
					'referenceNumber' (map.get('RelatedRef'))
					'amountInfo'{
						'amount' (map.get('Amount'))
						'currency' (map.get('Currency'))
					}
					'remark' (map.get('Description'))
					'bookingBranchId' (map.get('Book24'))
					'service' (service1.toString())
					'serviceChannel' (channel1.toString())
					'debitAccNumber' (map.get('DebitAccNo'))
					'creditAccNumber' (map.get('CreditAccNo')) 
					'transactionType' (type1.toString())
					'debitValueDate' (valueDate)
				}
			
			}
		}
	
		def msg = new String(response.httpResponse.getData())
		logger.info "Log RS: " + msg
		
		if(response.httpResponse.statusMessage=="OK" ) {
			msg = msg.substring(msg.indexOf("resultCode"), msg.indexOf("></asbo:makeFundTransferRs>"))
			
			if(msg.contains("resultCode=\"0\"")){
				greeting = 'FFC 00-CASA: Send Successfully'
				def ft = msg.substring(msg.indexOf("<transactionId>") + 15, msg.indexOf("</transactionId>"))
				updateDescription(db, logger, mailHeader_Id, ft)
			}else{
				greeting = "FFC 01-CASA:" + msg
			}
			
		}else{
			greeting = 'FFC 01-CASA: Send Faild'
		}
	}
	catch (Exception sfe) {
		logger.error "Error:" + sfe.message
		greeting = "FFC 02-CASA: " + sfe.message
		sendMailNotify(1, "ERROR webservice makeFundTransferRq", sfe.message)
	}
	
def builder = new StreamingMarkupBuilder()
def xmlOut = builder.bind{
		mkp.declareNamespace('':'PaymentsWS.wsdl')
		'PaymentsMsgReply' (greeting)
		}.toString()
logger.info "response created = ${xmlOut}"
if(db != null){
	db.close()
}

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
			sendMailNotify(1, "ERROR write file dien CASA: ", ex.toString())
			return 1
		}
		return 0
	}

def Map getField(String data, Logger logger)
{
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
				//logger.info "Data: " + f79Tag + "  " + f79Value
				map.put(f79Tag, f79Value)	
			}
		}
	}catch(Exception ex){
		sendMailNotify(1, "ERROR format dien CASA: ", ex.toString())
	}
	
	return map
}

def String getRequestSoap(def map, def service1, def channel1, def transactionType1){

	Date date = Date.parse( 'dd/MM/yyyy', map.get('ValueDate') )
	//def valueDate = date.format('yyyyMMdd')
	def valueDate = '20200101'
	def requestCasa = '<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:asb="http://www.vpbank.com.vn/ASBOTreasuryOut" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">\n <soapenv:Header/>\n <soapenv:Body>\n'
	requestCasa += '<asb:makeFundTransferRq sessionId=TTS' + map.get('RelatedRef') + ' xsi:type="asb:MakeCACATransferRqType">\n'
	requestCasa += '<referenceNumber>' + map.get('RelatedRef') + '</referenceNumber>\n'
	requestCasa += '<amountInfo>\n<amount>' + map.get('Amount') + '</amount>\n<currency>' + map.get('Currency') + '</currency>\n</amountInfo>\n'
	requestCasa += '<remark>' + map.get('Description') + '</remark>\n'
	requestCasa += '<bookingBranchId>' + map.get('Book24') + '</bookingBranchId>\n'
	requestCasa += '<service>' + service1 + '</service>\n'
	requestCasa += '<serviceChannel>' + channel1 + '</serviceChannel>\n'
	requestCasa += '<debitAccNumber>' + map.get('DebitAccNo') + '</debitAccNumber>\n'
	requestCasa += '<creditAccNumber>' + map.get('CreditAccNo') + '</creditAccNumber>\n'
	requestCasa += '<transactionType>' + transactionType1 + '</transactionType>\n'
	requestCasa += '<debitValueDate>' + valueDate + '</debitValueDate>\n'
	requestCasa += '</asb:makeFundTransferRq>\n</soapenv:Body>\n</soapenv:Envelope>'
	
	return requestCasa
}

def void sendMailNotify(def message_type, def subjectsuffix, def logger){
	def proc = Runtime.getRuntime().exec(
		"/data/kplusaddon/FFC/config/File2Kondor/sendMail.sh $message_type $subjectsuffix $logger")
}

def String getPaymentClass(Logger logger, def db, String referent){
	def sql = "select Kustom.dbo.fn_getPayClass_ShortName($referent) as PaymentClass"
	def data = ""
	try{
		db.eachRow(sql){ rs ->
		data = rs.PaymentClass
		logger.info "Data Import: " + rs.PaymentClass
		}
		return data
	}catch(Exception ex){
		logger.error "Get PaymentClass_ShortName error: " + ex.getMessage()
		return data
	}
}

def boolean updateDescription(def db, Logger logger, def mailHeader_Id, def ft){
	def bool
	try{
		def sql = "update ktpplus..MailHeader set Description = 'Core banking GL-GL Message " + ft + "' where MailHeader_Id = " + mailHeader_Id
		logger.info sql
		bool = db.execute(sql)
	}catch(Exception ex){
		logger.error "update Description MailHeader " + ex.getMessage()
		return bool
	}
	return bool
}