package qslv.transaction.rest;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import qslv.transaction.request.CancelReservationRequest;
import qslv.transaction.request.CommitReservationRequest;
import qslv.transaction.request.ReservationRequest;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.request.TransactionSearchRequest;
import qslv.transaction.request.TransferAndTransactRequest;
import qslv.transaction.response.CancelReservationResponse;
import qslv.transaction.response.CommitReservationResponse;
import qslv.transaction.response.ReservationResponse;
import qslv.transaction.response.TransactionResponse;
import qslv.transaction.response.TransactionSearchResponse;
import qslv.transaction.response.TransferAndTransactResponse;
import qslv.common.TimedResponse;
import qslv.common.TraceableRequest;
import qslv.util.LogRequestTracingData;
import qslv.util.ServiceElapsedTimeSLI;

/**
 * Transaction Resource REST Service. Manages a transaction journal and running
 * account balance.
 * 
 * Transactions can be reservations, conditional on sufficient funds.
 * Reservations require cancel or commit of the reservation, managed outside of
 * this resource controller. A Resource Cancel will return reserved funds back
 * to the account. Reservation Commit can alter the original transaction amount,
 * by specifying a replacement amount. The difference is automatically
 * calculated.
 * 
 * If a reservation is not used the transaction will be forced, with no
 * condition on sufficient funds.
 * 
 * Previous transactions are never modified. Only new journal entries are made.
 * The running balance is updated in a transaction with the journal entry, and
 * so is guaranteed to be accurate.
 * 
 * Account-ID is not validated. If no account is found a new account is created
 * within the entity.
 * 
 * Idempotency is guaranteed based on client generated request-uuid. When a
 * matching request-uuid is found the previous transaction is returned.
 * 
 * All HTTP Headers are required to contain: Business-Taxonomy-ID - string
 * Business-Taxonomy-Version - string Correlation-ID - string AIT-ID - string
 * 
 * PUT /reservation PUT /transaction Request Body must contain all required
 * Transaction Request fields: request_uuid, account_id, transaction_am,
 * transactionMetaData_json. Optional: debitCard_id; Responses HTTP-Status 400
 * Bad Request - required fields not present HTTP-Status 500
 * INTERNAL_SERVER_ERROR - an unrecoverable internal error has occurred
 * HTTP-Status 201 Created Response status code 0 - success Response status code
 * 1 - Request with request_uuid already made Body: Transaction Resource
 * HTTP-Status 400 Bad Request - required fields not present HTTP-Status 406 Not
 * Acceptable Response status code 2 - Insufficient Funds (reservation)
 * 
 * PUT /reservationCommit PUT /reservationCancel Request Body must contain all
 * required ReservationRequest fields: private UUID request_uuid,
 * reservation_uuid, transaction_am, transactionMetaData_json.
 * /reservationCommit will adjust the transaction_am based by the amount on the
 * reservation. Care should be taken to include the amount in the Metadata.
 * /reservationCancel will use the negative (-1) transaction_am from the
 * reservation. Responses: HTTP-Status 201 Created Response status code 0 -
 * success Response status code 1 - Request with request_uuid already made Body:
 * Transaction Resource HTTP-Status 404 Not Found - the reservation_uuid could
 * not be found HTTP-Status 409 Conflict - the reservation_uuid was already
 * committed/canceled
 * 
 * GET /transaction
 */

@RestController
public class TransactionController {
	private static final Logger log = LoggerFactory.getLogger(TransactionController.class);

	@Autowired
	public ConfigProperties props;
	@Autowired
	private TransactionService service;

	public TransactionService getService() {
		return service;
	}

	public void setConfigProperties(ConfigProperties configProperties) {
		props = configProperties;
	}
	public void setService(TransactionService service) {
		this.service = service;
	}

	@PostMapping("/Transaction")
	@ResponseStatus(HttpStatus.CREATED)
	@ResponseBody
	@LogRequestTracingData(value="POST/Transaction", ait = "33333")
	@ServiceElapsedTimeSLI(value="POST/Transaction", injectResponse = true, ait = "44444")
	public TimedResponse<TransactionResponse> postTransaction(@RequestHeader Map<String, String> headers,
			@RequestBody TransactionRequest request) {

		validateHeaders(headers);
		validateTransactionRequest(request);
		if (false == headers.get(TraceableRequest.ACCEPT_VERSION).equals(TransactionRequest.VERSION_1_0)) {
			log.error("postTransaction, Invalid version {}",headers.get(TraceableRequest.ACCEPT_VERSION));
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid version "+headers.get(TraceableRequest.ACCEPT_VERSION));
		}
		TransactionResponse response = service.createTransaction(request);

		return new TimedResponse<TransactionResponse>(response);
	}

	@PostMapping("/Reservation")
	@ResponseStatus(HttpStatus.CREATED)
	@ResponseBody
	@LogRequestTracingData(value="POST/Reservation", ait = "33333")
	@ServiceElapsedTimeSLI(value="POST/Reservation", injectResponse = true, ait = "44444")
	public TimedResponse<ReservationResponse> postReservation(@RequestHeader Map<String, String> headers,
			@RequestBody ReservationRequest request) {

		validateHeaders(headers);
		validateReservationRequest(request);
		if (false == headers.get(TraceableRequest.ACCEPT_VERSION).equals(ReservationRequest.VERSION_1_0)) {
			log.error("postReservation, Invalid version {}",headers.get(TraceableRequest.ACCEPT_VERSION));
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid version "+headers.get(TraceableRequest.ACCEPT_VERSION));
		}

		ReservationResponse response = service.createReservation(request);

		return new TimedResponse<ReservationResponse>(response);
	}

	@PostMapping("/CommitReservation")
	@ResponseStatus(HttpStatus.CREATED)
	@ResponseBody
	@LogRequestTracingData(value="POST/CommitReservation", ait = "33333")
	@ServiceElapsedTimeSLI(value="POST/CommitReservation", injectResponse = true, ait = "44444")
	public TimedResponse<CommitReservationResponse> postCommitReservation(@RequestHeader Map<String, String> headers,
			@RequestBody CommitReservationRequest request) {
		
		validateHeaders(headers);
		validateCommitReservationRequest(request);
		if (false == headers.get(TraceableRequest.ACCEPT_VERSION).equals(CommitReservationRequest.VERSION_1_0)) {
			log.error("postCommitReservation, Invalid version {}",headers.get(TraceableRequest.ACCEPT_VERSION));
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid version "+headers.get(TraceableRequest.ACCEPT_VERSION));
		}
		
		CommitReservationResponse response = service.commitReservation(request);

		return new TimedResponse<CommitReservationResponse>(response);
	}

	@PostMapping("/CancelReservation")
	@ResponseStatus(HttpStatus.CREATED)
	@ResponseBody
	@LogRequestTracingData(value="POST/CancelReservation", ait = "33333")
	@ServiceElapsedTimeSLI(value="POST/CancelReservation", injectResponse = true, ait = "44444")
	public TimedResponse<CancelReservationResponse> postCancelReservation(@RequestHeader Map<String, String> headers,
			@RequestBody CancelReservationRequest request) {

		validateHeaders(headers);
		validateCancelReservationRequest(request);
		if (false == headers.get(TraceableRequest.ACCEPT_VERSION).equals(CancelReservationRequest.VERSION_1_0)) {
			log.error("postCancelReservation, Invalid version {}",headers.get(TraceableRequest.ACCEPT_VERSION));
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid version "+headers.get(TraceableRequest.ACCEPT_VERSION));
		}
		CancelReservationResponse response = service.cancelReservation(request);

		return new TimedResponse<CancelReservationResponse>(response);
	}

	@PostMapping("/TransferAndTransact")
	@ResponseStatus(HttpStatus.CREATED)
	@ResponseBody
	@LogRequestTracingData(value="POST/TransferAndTransact", ait = "33333")
	@ServiceElapsedTimeSLI(value="POST/TransferAndTransact", injectResponse = true, ait = "44444")
	public TimedResponse<TransferAndTransactResponse> postTransferAndTransact(@RequestHeader Map<String, String> headers,
			@RequestBody TransferAndTransactRequest request) {
		
		validateHeaders(headers);
		validateTransferAndTransactRequest(request);
		if (false == headers.get(TraceableRequest.ACCEPT_VERSION).equals(TransferAndTransactRequest.VERSION_1_0)) {
			log.error("postCancelReservation, Invalid version {}",headers.get(TraceableRequest.ACCEPT_VERSION));
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid version "+headers.get(TraceableRequest.ACCEPT_VERSION));
		}

		TransferAndTransactResponse response = service.transferAndTransact(request);

		return new TimedResponse<TransferAndTransactResponse>(response);
	}
	@GetMapping("/Transaction")
	@ResponseStatus(HttpStatus.OK)
	@ResponseBody
	@LogRequestTracingData(value="GET/Transaction", ait = "33333")
	@ServiceElapsedTimeSLI(value="GET/Transaction", injectResponse = true, ait = "44444")
	public TimedResponse<TransactionSearchResponse> getTransaction(@RequestHeader Map<String, String> headers,
			@RequestBody TransactionSearchRequest request) {
		validateHeaders(headers);
		validateTransactionSearchRequest(request);
		if (false == headers.get(TraceableRequest.ACCEPT_VERSION).equals(TransactionSearchRequest.VERSION_1_0)) {
			log.error("getTransaction, Invalid version {}",headers.get(TraceableRequest.ACCEPT_VERSION));
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid version "+headers.get(TraceableRequest.ACCEPT_VERSION));
		}
		
		TransactionSearchResponse response = service.findTransaction(request);
		
		return new TimedResponse<TransactionSearchResponse>(0, response);
	}

	private void validateTransactionRequest(TransactionRequest request) {
		log.trace("validateTransactionRequest ENTRY");
		if (request.getRequestUuid() == null) {
			log.error("controller.validateTransactionRequest, Malformed Request. Missing request_uuid");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing request_uuid");
		}

		if (request.getAccountNumber() == null || request.getAccountNumber().length() <= 1) {
			log.error("controller.validateTransactionRequest Malformed Request. Missing account_id");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing account_id");
		}

		if (request.getTransactionMetaDataJson() == null || request.getTransactionMetaDataJson().length() <= 1) {
			log.error("controller.validateTransactionRequest Malformed Request. Missing transactionMetaData_json");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing transactionMetaData_json");
		}
		
		if (request.getTransactionAmount() == 0) {
			log.error("controller.validateTransactionRequest Malformed Request. Transaction Amount must not be zero(0).");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction Amount must not be zero(0).");
		}
	}

	private void validateReservationRequest(ReservationRequest request) {
		log.trace("validateReservationRequest ENTRY");
		if (request.getRequestUuid() == null) {
			log.error("controller.validateTransactionRequest, Malformed Request. Missing request_uuid");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing request_uuid");
		}

		if (request.getAccountNumber() == null || request.getAccountNumber().length() <= 1) {
			log.error("controller.validateTransactionRequest Malformed Request. Missing account_id");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing account_id");
		}

		if (request.getTransactionMetaDataJson() == null || request.getTransactionMetaDataJson().length() <= 1) {
			log.error("controller.validateTransactionRequest Malformed Request. Missing transactionMetaData_json");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing transactionMetaData_json");
		}
		
		if (request.getTransactionAmount() == 0) {
			log.error("controller.validateTransactionRequest Malformed Request. Transaction Amount must not be zero(0).");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction Amount must not be zero(0).");
		}
	}
	
	private void validateCancelReservationRequest(CancelReservationRequest request) {
		log.trace("validateReservationRequest ENTRY");
		if (request.getRequestUuid() == null) {
			log.error("controller.validateReservationRequest Malformed Request. Missing request_uuid");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing request_uuid");
		}

		if (request.getReservationUuid() == null) {
			log.error("controller.validateReservationRequest Malformed Request. Missing reservation_uuid");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing reservation_uuid");
		}

		if (request.getTransactionMetaDataJson() == null || request.getTransactionMetaDataJson().length() <= 1) {
			log.error("controller.validateReservationRequest Malformed Request. Missing transactionMetaData_json");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing transactionMetaData_json");
		}
	}
	
	private void validateCommitReservationRequest(CommitReservationRequest request) {
		log.trace("validateReservationRequest ENTRY");
		if (request.getRequestUuid() == null) {
			log.error("controller.validateReservationRequest Malformed Request. Missing request_uuid");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing request_uuid");
		}

		if (request.getReservationUuid() == null) {
			log.error("controller.validateReservationRequest Malformed Request. Missing reservation_uuid");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing reservation_uuid");
		}

		if (request.getTransactionMetaDataJson() == null || request.getTransactionMetaDataJson().length() <= 1) {
			log.error("controller.validateReservationRequest Malformed Request. Missing transactionMetaData_json");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing transactionMetaData_json");
		}
		if (request.getTransactionAmount() == 0) {
			log.error("controller.validateTransactionRequest Malformed Request. Transaction Amount must not be zero(0).");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction Amount must not be zero(0).");
		}
	}
	
	private void validateTransferAndTransactRequest(TransferAndTransactRequest request) {
		log.trace("validateTransferAndTransactRequest ENTRY");
		
		if (request.getRequestUuid() == null) {
			log.error("controller.validateTransferAndTransactRequest Malformed Request. Missing requestUuid");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing requestUuid");
		}	
		if (request.getTransactionRequest() == null) {
			log.error("controller.validateTransferAndTransactRequest Malformed Request. Missing Transaction Request");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Transaction Request");
		}
		if (request.getTransferReservation() == null) {
			log.error("controller.validateTransferAndTransactRequest Malformed Request. Missing Transfer Reservation");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Transfer Reservation");
		}
		if (request.getTransferReservation().getTransactionUuid() == null) {
			log.error("controller.validateTransferAndTransactRequest Malformed Request. Missing Transfer Reservation UUID");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing Transfer Reservation UUID");
		}
		if (request.getTransferReservation().getTransactionAmount() >= 0L) {
			log.error("controller.validateTransferAndTransactRequest Malformed Request. Transfer Reservation must LT Zero.");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transfer Reservation must LT Zero.");
		}
		if (request.getTransactionRequest().getAccountNumber() == null 
				|| request.getTransactionRequest().getAccountNumber().length() <= 1) {
			log.error("controller.validateTransactionRequest Malformed Request. Missing accountNumber");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing accountNumber");
		}
		if (request.getTransactionRequest().getTransactionMetaDataJson() == null 
				|| request.getTransactionRequest().getTransactionMetaDataJson().length() <= 1) {
			log.error("controller.validateTransactionRequest Malformed Request. Missing transactionMetaDataJson");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing transactionMetaDataJson");
		}
		if (request.getTransactionRequest().getTransactionAmount() >= 0L) {
			log.error("controller.validateTransferAndTransactRequest Malformed Request. Transaction must LT Zero.");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Transaction must LT Zero.");
		}
	}
	
	private void validateTransactionSearchRequest(TransactionSearchRequest request) {
		log.trace("validateHeaders ENTRY");
		int count = 0;
		if (request.getTransactionUuid() == null) count++;
		if (request.getReservationUuid() == null) count++;
		
		if (count != 1) {
			log.error("controller.validateTransactionSearchRequest Malformed Request. Missing exactly one transaction_uuid or account_id");
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing exactly one transaction_uuid or account_id");
		}	
	}

	private void validateHeaders(Map<String, String> headerMap) {
		log.trace("validateHeaders ENTRY");

		if (headerMap.get(TraceableRequest.AIT_ID) == null) {
			log.error("controller.validateHeaders, Malformed Request. Missing header variable {}", TraceableRequest.AIT_ID);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing header variable "+TraceableRequest.AIT_ID);
		}
		if (headerMap.get(TraceableRequest.BUSINESS_TAXONOMY_ID) == null) {
			log.error("controller.validateHeaders, Malformed Request. Missing header variable {}",TraceableRequest.BUSINESS_TAXONOMY_ID);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing header variable "+TraceableRequest.BUSINESS_TAXONOMY_ID);
		}
		if (headerMap.get(TraceableRequest.CORRELATION_ID) == null) {
			log.error("controller.validateHeaders, Malformed Request. Missing header variable {}",TraceableRequest.CORRELATION_ID);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing header variable "+TraceableRequest.CORRELATION_ID);
		}
		if (headerMap.get(TraceableRequest.ACCEPT_VERSION) == null) {
			log.error("controller.validateHeaders, Malformed Request. Missing header variable {}",TraceableRequest.ACCEPT_VERSION);
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Missing header variable "+TraceableRequest.ACCEPT_VERSION);
		}
	}

}
