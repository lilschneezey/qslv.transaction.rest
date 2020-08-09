package qslv.transaction.rest;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import qslv.transaction.request.CancelReservationRequest;
import qslv.transaction.request.CommitReservationRequest;
import qslv.transaction.request.ReservationRequest;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.request.TransactionSearchRequest;
import qslv.transaction.request.TransferAndTransactRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.CancelReservationResponse;
import qslv.transaction.response.CommitReservationResponse;
import qslv.transaction.response.ReservationResponse;
import qslv.transaction.response.TransactionResponse;
import qslv.transaction.response.TransactionSearchResponse;
import qslv.transaction.response.TransferAndTransactResponse;

@Service
public class TransactionService {
	private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

	@Autowired
	private JdbcDao jdbcDao;

	public void setJdbcDao(JdbcDao dao) {
		this.jdbcDao = dao;
	}

	@Transactional
	public TransactionResponse createTransaction(TransactionRequest request) {
		log.trace("service.createTransaction ENTRY");

		TransactionResource idempotent = jdbcDao.checkIdempotency(request.getRequestUuid(), request.getAccountNumber());
		if (idempotent != null) {
			if (idempotent.getTransactionTypeCode().equals(TransactionResource.REJECTED_TRANSACTION)) {
				return new TransactionResponse(TransactionResponse.INSUFFICIENT_FUNDS,idempotent);
			} else {
				return new TransactionResponse(TransactionResponse.SUCCESS,idempotent);
			}
		}

		long runningBalance_am = jdbcDao.selectBalanceForUpdate(request.getAccountNumber());

		TransactionResource resource = new TransactionResource();
		resource.setRequestUuid(request.getRequestUuid());
		resource.setAccountNumber(request.getAccountNumber());
		resource.setDebitCardNumber(request.getDebitCardNumber());
		resource.setTransactionAmount(request.getTransactionAmount());
		resource.setTransactionMetaDataJson(request.getTransactionMetaDataJson());
		TransactionResponse response = new TransactionResponse(TransactionResponse.SUCCESS, resource);

		if (request.isAuthorizeAgainstBalance()
			&& request.getTransactionAmount() < 0 
			&& runningBalance_am < Math.abs(request.getTransactionAmount()) ) {
			
			log.debug("createTransaction Insuffienct Funds. Balance: {}, Transaction Amount {}", runningBalance_am, request.getTransactionAmount());
			resource.setTransactionTypeCode(TransactionResource.REJECTED_TRANSACTION);
			resource.setRunningBalanceAmount(runningBalance_am);
			response.setStatus(TransactionResponse.INSUFFICIENT_FUNDS);
			jdbcDao.insertTransaction(resource);
			
		} else {
			log.debug("createTransaction Approved. Balance: {}, Transaction Amount {}", runningBalance_am, request.getTransactionAmount());

			resource.setTransactionTypeCode(TransactionResource.NORMAL);
			runningBalance_am += resource.getTransactionAmount();
			resource.setRunningBalanceAmount(runningBalance_am);
			response.setStatus(TransactionResponse.SUCCESS);
			jdbcDao.upsertBalance(resource.getAccountNumber(), resource.getRunningBalanceAmount());
			jdbcDao.insertTransaction(resource);
		}

		log.trace("service.createTransaction EXIT");
		return response;
	}

	@Transactional
	public ReservationResponse createReservation(ReservationRequest request) {
		log.trace("createReservation ENTRY");

		TransactionResource idempotent = jdbcDao.checkIdempotency(request.getRequestUuid(), request.getAccountNumber());
		if (idempotent != null) {
			if (idempotent.getTransactionTypeCode().equals(TransactionResource.REJECTED_TRANSACTION)) {
				return new ReservationResponse(ReservationResponse.INSUFFICIENT_FUNDS,idempotent);
			} else {
				return new ReservationResponse(ReservationResponse.SUCCESS,idempotent);
			}
		}

		long runningBalance_am = jdbcDao.selectBalanceForUpdate(request.getAccountNumber());

		TransactionResource resource = new TransactionResource();
		resource.setRequestUuid(request.getRequestUuid());
		resource.setAccountNumber(request.getAccountNumber());
		resource.setDebitCardNumber(request.getDebitCardNumber());
		resource.setTransactionAmount(request.getTransactionAmount());
		resource.setTransactionMetaDataJson(request.getTransactionMetaDataJson());

		// Asset account. DEBITS are positive, CREDITS are negative
		// Reject if 1) reservation for a 2) credit that is 3) more than the current
		// balance
		int restResponseCode;
		if (request.isAuthorizeAgainstBalance()
			&& request.getTransactionAmount() < 0 
			&& runningBalance_am < Math.abs(request.getTransactionAmount())) {

			log.debug("createReservation Insuffienct Funds. Balance: {}, Transaction Amount {}", runningBalance_am, request.getTransactionAmount());
			resource.setTransactionTypeCode(TransactionResource.REJECTED_TRANSACTION);
			resource.setRunningBalanceAmount(runningBalance_am);

			jdbcDao.insertTransaction(resource);
			restResponseCode = TransactionResponse.INSUFFICIENT_FUNDS;
		} else {

			log.debug("createReservation Approved. Balance: {}, Transaction Amount {}", runningBalance_am, request.getTransactionAmount());
			resource.setTransactionTypeCode(TransactionResource.RESERVATION);
			runningBalance_am += resource.getTransactionAmount();
			resource.setRunningBalanceAmount(runningBalance_am);

			jdbcDao.upsertBalance(resource.getAccountNumber(), runningBalance_am);
			jdbcDao.insertTransaction(resource);
			restResponseCode = TransactionResponse.SUCCESS;
		}

		log.trace("createReservation EXIT");
		return new ReservationResponse(restResponseCode, resource);
	}

	@Transactional
	public CommitReservationResponse commitReservation(CommitReservationRequest request) {
		log.trace("commitReservation ENTRY");

		TransactionResource idempotent = jdbcDao.checkIdempotency(request.getRequestUuid(), request.getAccountNumber());
		if (idempotent != null) {
			return new CommitReservationResponse(CommitReservationResponse.SUCCESS,idempotent);
		}

		TransactionResource reservation = jdbcDao.findReservation(request.getReservationUuid());
		jdbcDao.verifyReservationOpen(request.getReservationUuid());
		long runningBalance_am = jdbcDao.selectBalanceForUpdate(reservation.getAccountNumber());

		TransactionResource resource = new TransactionResource();
		resource.setRequestUuid(request.getRequestUuid());
		resource.setAccountNumber(reservation.getAccountNumber());
		resource.setDebitCardNumber(reservation.getDebitCardNumber());
		resource.setTransactionTypeCode(TransactionResource.RESERVATION_COMMIT);
		resource.setReservationUuid(request.getReservationUuid());
		resource.setTransactionMetaDataJson(request.getTransactionMetaDataJson());

		if (reservation.getTransactionAmount() == request.getTransactionAmount()) {
			resource.setTransactionAmount(0L);
			resource.setRunningBalanceAmount(runningBalance_am);
		} else {
			resource.setTransactionAmount(request.getTransactionAmount() - reservation.getTransactionAmount());
			runningBalance_am += resource.getTransactionAmount();
			resource.setRunningBalanceAmount(runningBalance_am);
			jdbcDao.upsertBalance(reservation.getAccountNumber(), runningBalance_am);
		}

		jdbcDao.insertCommitOrCancel(resource);

		log.trace("commitReservation EXIT");
		return new CommitReservationResponse(TransactionResponse.SUCCESS, resource);
	}

	@Transactional
	public CancelReservationResponse cancelReservation(CancelReservationRequest request) {
		log.trace("service.cancelReservation ENTRY");

		TransactionResource idempotent = jdbcDao.checkIdempotency(request.getRequestUuid(), request.getAccountNumber());
		if (idempotent != null) {
			return new CancelReservationResponse(CancelReservationResponse.SUCCESS,idempotent);
		}

		TransactionResource reservation = jdbcDao.findReservation(request.getReservationUuid());
		jdbcDao.verifyReservationOpen(request.getReservationUuid());
		long runningBalance_am = jdbcDao.selectBalanceForUpdate(reservation.getAccountNumber());

		runningBalance_am -= reservation.getTransactionAmount();

		TransactionResource resource = new TransactionResource();
		resource.setRequestUuid(request.getRequestUuid());
		resource.setAccountNumber(reservation.getAccountNumber());
		resource.setDebitCardNumber(reservation.getDebitCardNumber());
		resource.setTransactionAmount(0L - reservation.getTransactionAmount());
		resource.setTransactionTypeCode(TransactionResource.RESERVATION_CANCEL);
		resource.setRunningBalanceAmount(runningBalance_am);
		resource.setReservationUuid(request.getReservationUuid());
		resource.setTransactionMetaDataJson(request.getTransactionMetaDataJson());

		jdbcDao.upsertBalance(reservation.getAccountNumber(), runningBalance_am);
		jdbcDao.insertCommitOrCancel(resource);

		log.trace("service.cancelReservation EXIT");
		return new CancelReservationResponse(TransactionResponse.SUCCESS, resource);
	}
	
	public TransactionSearchResponse findTransaction(TransactionSearchRequest request) {
		log.trace("service.findTransaction ENTRY");
		
		TransactionSearchResponse response = new TransactionSearchResponse();
		if ( null != request.getTransactionUuid() ) {
			response.setTransactions( Collections.singletonList(jdbcDao.findTransaction(request.getTransactionUuid())) );
		} else if (null != request.getReservationUuid() ) {
			response.setTransactions( jdbcDao.findRelatedToReservation(request.getReservationUuid()));
		}
		
		log.trace("service.findTransaction EXIT");
		return response;
	}
	
	@Transactional
	public TransferAndTransactResponse transferAndTransact(TransferAndTransactRequest request) {
		log.trace("service.transferAndTransact ENTRY");

		List<TransactionResource> idempotent = jdbcDao.checkMultiIdempotency(request.getRequestUuid(), 
				request.getTransactionRequest().getAccountNumber());
		if (idempotent != null && idempotent.size() == 2) {
			log.debug("service.TransferAndTransactResponse: Already present for {}", request.getRequestUuid());
			return new TransferAndTransactResponse(TransferAndTransactResponse.SUCCESS, idempotent);
		} else if (idempotent != null && idempotent.size() != 0) {
			log.error("Expected 2 transactions but got {} for Request UUID {}", idempotent.size(), request.getRequestUuid().toString());
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
				String.format("Expected 2 transactions and found %d for Request UUID %s.", idempotent.size(), request.getRequestUuid().toString()));			
		}

		long runningBalance_am = jdbcDao.selectBalanceForUpdate(request.getTransactionRequest().getAccountNumber());

		TransactionResource transfer = new TransactionResource();
		transfer.setAccountNumber(request.getTransactionRequest().getAccountNumber());
		transfer.setDebitCardNumber(null);
		transfer.setRequestUuid(request.getRequestUuid());
		transfer.setReservationUuid(null);		
		transfer.setTransactionMetaDataJson(request.getTransferReservation().getTransactionMetaDataJson());
		transfer.setTransactionTypeCode(TransactionResource.NORMAL);

		log.debug("transferAndTransact 1)Transfer: {} + {} = {}", runningBalance_am, (0L-request.getTransferReservation().getTransactionAmount()), 
				(runningBalance_am - request.getTransferReservation().getTransactionAmount()));
		transfer.setTransactionAmount(Math.subtractExact(0L, request.getTransferReservation().getTransactionAmount()));
		runningBalance_am += transfer.getTransactionAmount();
		transfer.setRunningBalanceAmount(runningBalance_am);
		
		jdbcDao.insertTransaction(transfer);

		TransactionResource transact = new TransactionResource();
		transact.setAccountNumber(request.getTransactionRequest().getAccountNumber());
		transact.setDebitCardNumber(request.getTransactionRequest().getDebitCardNumber());
		transact.setRequestUuid(request.getRequestUuid());
		transact.setReservationUuid(null);
		transact.setTransactionAmount(request.getTransactionRequest().getTransactionAmount());
		transact.setTransactionMetaDataJson(request.getTransactionRequest().getTransactionMetaDataJson());
		transact.setTransactionTypeCode(TransactionResource.NORMAL);
		
		log.debug("transferAndTransact 2)Transact: {} - {} = {}", runningBalance_am, (0L-request.getTransactionRequest().getTransactionAmount()), 
				(runningBalance_am + request.getTransferReservation().getTransactionAmount()));
		transact.setTransactionAmount(request.getTransactionRequest().getTransactionAmount());
		runningBalance_am += transact.getTransactionAmount();
		transact.setRunningBalanceAmount(runningBalance_am);

		jdbcDao.insertTransaction(transact);
		jdbcDao.upsertBalance(request.getTransactionRequest().getAccountNumber(), runningBalance_am);
		
		TransferAndTransactResponse response = new TransferAndTransactResponse(TransferAndTransactResponse.SUCCESS, 
				new ArrayList<TransactionResource>());
		response.getTransactions().add(transfer);
		response.getTransactions().add(transact);
		
		return response;
	}
}