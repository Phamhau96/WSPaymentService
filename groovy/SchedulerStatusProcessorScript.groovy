import com.misys.tools.integration.api.message.GMessage
import com.misys.tools.integration.api.message.GStatus

GMessage incomingMsg = message

def result = incomingMsg.messageBody
def successful = result.successful
def response = result.response

def status
if (successful) {
    status = GStatus.SUCCESSFUL
} else {
    status = GStatus.WARNING
}

return incomingMsg
        .result()
        .status(status)
        .messageBody(response)
        .build()