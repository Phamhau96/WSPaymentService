import com.misys.tools.integration.api.message.GMessage
import groovy.util.slurpersupport.GPathResult
import com.misys.tools.integration.api.annotation.InjectResource
import groovy.transform.Field
import groovy.sql.Sql
import java.sql.*
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.util.Date
import wslite.soap.*

final Logger logger = LoggerFactory.getLogger("SWIFTsftp")

GMessage message = message

def textMessage = message.messageBodyAsText

def csv = textMessage.split("\n")
def data = ''
for(int i = 0; i < csv.size(); i++){
	data += csv[i] + '\r\n'
}

return message
        .result()
        .messageBody(data)
        .build()

		
		
