package qslv.transaction.rest;

import java.util.Collections;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import qslv.transaction.request.CancelReservationRequest;
import qslv.transaction.request.CommitReservationRequest;
import qslv.transaction.request.TransactionRequest;
import qslv.transaction.request.TransactionSearchRequest;
import qslv.transaction.resource.TransactionResource;
import qslv.transaction.response.CancelReservationResponse;
import qslv.transaction.response.CommitReservationResponse;
import qslv.transaction.response.ReservationResponse;
import qslv.transaction.response.TransactionResponse;
import qslv.transaction.response.TransactionSearchResponse;

@Service
public class TransactionService {
	private static final Logger log = LoggerFactory.getLogger(TransactionService.class);

	@Autowired
	private TransactionDAO dao;

	public TransactionDAO getDao() {
		return dao;
	}

	public void setDao(TransactionDAO dao) {
		this.dao = dao;
	}

	@Transactional
	public TransactionResponse createTransaction(TransactionRequest request) {
		log.trace("service.createTransaction ENTRY");

		TransactionResource resource = dao.checkIdempotency(request.getRequestUuid());
		if (resource != null) {
			return new TransactionResponse(TransactionResponse.ALREADY_PRESENT,resource);
		}

		long runningBalance_am = dao.selectBalanceForUpdate(request.getAccountNumber());

		resource = new TransactionResource();
		resource.setRequestUuid(request.getRequestUuid());
		resource.setAccountNumber(request.getAccountNumber());
		resource.setDebitCardNumber(request.getDebitCardNumber());
		resource.setTransactionAmount(request.getTransactionAmount());
		resource.setTransactionMetaDataJson(request.getTransactionMetaDataJson());
		TransactionResponse response = new TransactionResponse(TransactionResponse.SUCCESS, resource);

		if (request.getTransactionAmount() < 0 && runningBalance_am < Math.abs(request.getTransactionAmount())) {
			log.debug("createTransaction Insuffienct Funds. Balance: {}, Transaction Amount {}", runningBalance_am, request.getTransactionAmount());
			resource.setTransactionTypeCode(TransactionResource.REJECTED_TRANSACTION);
			resource.setRunningBalanceAmount(runningBalance_am);
			response.setStatus(TransactionResponse.INSUFFICIENT_FUNDS);
			dao.insertTransaction(resource);
		} else {
			resource.setTransactionTypeCode(TransactionResource.NORMAL);
			runningBalance_am += resource.getTransactionAmount();
			resource.setRunningBalanceAmount(runningBalance_am);
			response.setStatus(TransactionResponse.SUCCESS);
			dao.upsertBalance(resource.getAccountNumber(), resource.getRunningBalanceAmount());
			dao.insertTransaction(resource);
		}

		log.trace("service.createTransaction EXIT");
		return response;
	}

	public ReservationResponse createReservation(TransactionRequest request) {
		log.trace("createReservation ENTRY");

		TransactionResource resource;
		resource = dao.checkIdempotency(request.getRequestUuid());
		if (resource != null) {
			return new ReservationResponse(TransactionResponse.ALREADY_PRESENT, resource);
		}

		long runningBalance_am = dao.selectBalanceForUpdate(request.getAccountNumber());

		resource = new TransactionResource();
		resource.setRequestUuid(request.getRequestUuid());
		resource.setAccountNumber(request.getAccountNumber());
		resource.setDebitCardNumber(request.getDebitCardNumber());
		resource.setTransactionAmount(request.getTransactionAmount());
		resource.setTransactionMetaDataJson(request.getTransactionMetaDataJson());

		// Asset account. DEBITS are positive, CREDITS are negative
		// Reject if 1) reservation for a 2) credit that is 3) more than the current
		// balance
		int restResponseCode;
		if (request.getTransactionAmount() < 0 && runningBalance_am < Math.abs(request.getTransactionAmount())) {

			log.debug("createReservation Insuffienct Funds. Balance: {}, Transaction Amount {}", runningBalance_am, request.getTransactionAmount());
			resource.setTransactionTypeCode(TransactionResource.REJECTED_TRANSACTION);
			resource.setRunningBalanceAmount(runningBalance_am);

			dao.insertTransaction(resource);
			restResponseCode = TransactionResponse.INSUFFICIENT_FUNDS;
		} else {

			log.debug("createReservation Approved. Balance: {}, Transaction Amount {}", runningBalance_am, request.getTransactionAmount());
			resource.setTransactionTypeCode(TransactionResource.RESERVATION);
			runningBalance_am += resource.getTransactionAmount();
			resource.setRunningBalanceAmount(runningBalance_am);

			dao.upsertBalance(resource.getAccountNumber(), runningBalance_am);
			dao.insertTransaction(resource);
			restResponseCode = TransactionResponse.SUCCESS;
		}

		log.trace("createReservation EXIT");
		return new ReservationResponse(restResponseCode, resource);
	}

	public CommitReservationResponse commitReservation(CommitReservationRequest request) {
		log.trace("commitReservation ENTRY");

		TransactionResource resource;
		resource = dao.checkIdempotency(request.getRequestUuid());
		if (resource != null) {
			return new CommitReservationResponse(TransactionResponse.ALREADY_PRESENT, resource);
		}

		TransactionResource reservation = dao.findReservation(request.getReservationUuid());
		dao.verifyReservationOpen(request.getReservationUuid());
		long runningBalance_am = dao.selectBalanceForUpdate(reservation.getAccountNumber());

		resource = new TransactionResource();
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
			dao.upsertBalance(reservation.getAccountNumber(), runningBalance_am);
		}

		dao.insertTransaction(resource);

		log.trace("commitReservation EXIT");
		return new CommitReservationResponse(TransactionResponse.SUCCESS, resource);
	}

	public CancelReservationResponse cancelReservation(CancelReservationRequest request) {
		log.trace("service.cancelReservation ENTRY");

		TransactionResource resource;
		resource = dao.checkIdempotency(request.getRequestUuid());
		if (resource != null) {
			return new CancelReservationResponse(TransactionResponse.ALREADY_PRESENT, resource);
		}

		TransactionResource reservation = dao.findReservation(request.getReservationUuid());
		dao.verifyReservationOpen(request.getReservationUuid());
		long runningBalance_am = dao.selectBalanceForUpdate(reservation.getAccountNumber());

		runningBalance_am -= reservation.getTransactionAmount();

		resource = new TransactionResource();
		resource.setRequestUuid(request.getRequestUuid());
		resource.setAccountNumber(reservation.getAccountNumber());
		resource.setDebitCardNumber(reservation.getDebitCardNumber());
		resource.setTransactionAmount(0L - reservation.getTransactionAmount());
		resource.setTransactionTypeCode(TransactionResource.RESERVATION_CANCEL);
		resource.setRunningBalanceAmount(runningBalance_am);
		resource.setReservationUuid(request.getReservationUuid());
		resource.setTransactionMetaDataJson(request.getTransactionMetaDataJson());

		dao.upsertBalance(reservation.getAccountNumber(), runningBalance_am);
		dao.insertTransaction(resource);

		log.trace("service.cancelReservation EXIT");
		return new CancelReservationResponse(TransactionResponse.SUCCESS, resource);
	}
	
	public TransactionSearchResponse findTransaction(TransactionSearchRequest request) {
		log.trace("service.findTransaction ENTRY");
		
		TransactionSearchResponse response = new TransactionSearchResponse();
		if ( null != request.getTransactionUuid() ) {
			response.setTransactions( Collections.singletonList(dao.findTransaction(request.getTransactionUuid())) );
		} else if (null != request.getReservationUuid() ) {
			response.setTransactions( dao.findRelatedToReservation(request.getReservationUuid()));
		}
		
		log.trace("service.findTransaction EXIT");
		return response;
	}
}