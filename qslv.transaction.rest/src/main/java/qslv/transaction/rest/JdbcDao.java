package qslv.transaction.rest;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import qslv.transaction.resource.TransactionResource;
import qslv.util.ExternalResourceSLI;

@Repository
public class JdbcDao {
	private static final Logger log = LoggerFactory.getLogger(JdbcDao.class);

	@Autowired
	private JdbcTemplate jdbcTemplate;

	public void setJdbcTemplate(JdbcTemplate template) {
		this.jdbcTemplate = template;
	}


	/**
	 * selectBalanceForUpdate Lookup the running balance from the account_balance
	 * table and lock it
	 * 
	 * @param account_id the account to lookup
	 * @return The current running balance for the account
	 */
	public final static String getBalance_sql = "SELECT runningBalance_am from account_balance where account_id = ? FOR UPDATE;";

	@Transactional
	@ExternalResourceSLI(value="jdbc::selectBalanceForUpdate", ait = "88888", remoteFailures= {DataAccessException.class})
	public long selectBalanceForUpdate(final String account_id) {
		log.trace("selectBalanceForUpdate ENTRY");

		long runningBalance_am = 0;
		try {
			runningBalance_am = jdbcTemplate.queryForObject(getBalance_sql, Long.class, account_id);
			log.debug("selectBalanceForUpdate Account={} Balance={}", account_id, runningBalance_am);
		} catch (EmptyResultDataAccessException e) {
			log.debug("selectBalanceForUpdate Account=%s not found", account_id);
			// ignore; UPSERT will take care of missing data
		}
		return runningBalance_am;
	}

	/**
	 * insertTransaction inserts a new non-reservation row into the transaction
	 * table. Updates the provided resource with the created transaction_uuid.
	 * 
	 * @param resource The transaction resource to be inserted
	 */
	public final static String insert_transaction_sql = "INSERT INTO transaction(request_uuid, account_id, debitCard_id, transaction_am, "
			+ "transactionType_cd, runningBalance_am, transactionMetaData_json) VALUES (?,?,?,?,?,?,?);";

	@ExternalResourceSLI(value="jdbc::insertTransaction", ait = "88888", remoteFailures= {DataAccessException.class})
	@Transactional
	public void insertTransaction(TransactionResource resource) {

		log.trace("insertTransaction ENTRY");

		// Insert Transaction
		KeyHolder keyHolder = new GeneratedKeyHolder();

		jdbcTemplate.update(new PreparedStatementCreator() {
			@Override
			public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
				PreparedStatement ps = connection.prepareStatement(insert_transaction_sql,
						new String[] { "transaction_uuid" });
				ps.setObject(1, resource.getRequestUuid());
				ps.setObject(2, resource.getAccountNumber());
				ps.setObject(3, resource.getDebitCardNumber());
				ps.setObject(4, resource.getTransactionAmount());
				ps.setObject(5, resource.getTransactionTypeCode());
				ps.setObject(6, resource.getRunningBalanceAmount());
				ps.setObject(7, resource.getTransactionMetaDataJson());
				return ps;
			}
		}, keyHolder);

		try {
			resource.setTransactionUuid( (UUID) keyHolder.getKeys().get("transaction_uuid") );
			log.debug("insertTransaction UUID {}", resource.getTransactionUuid().toString());
		}
		catch (Exception e) {
			log.error("putTransaction, transaction_uuid not returned from insert statement. %s",
					insert_transaction_sql);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
					String.format("transaction_uuid not returned."));
		}
	}

	/**
	 * insertReservation inserts a new reservation row into the transaction table.
	 * Updates the provided resource with the created transaction_uuid.
	 * 
	 * @param resource The transaction resource to be inserted
	 */

	public final static String CommitOrCancelReservation_sql = "INSERT INTO transaction (request_uuid, account_id, debitCard_id, transaction_am, "
			+ "transactionType_cd, runningBalance_am, reservation_uuid, transactionMetaData_json) "
			+ "values (?,?,?,?,?,?,?,?);";

	@ExternalResourceSLI(value="jdbc::insertReservation", ait = "88888", remoteFailures= {DataAccessException.class})
	@Transactional
	public void insertCommitOrCancel(TransactionResource resource) {

		log.trace("insertCommitOrCancel ENTRY");

		// Insert Transaction
		KeyHolder keyHolder = new GeneratedKeyHolder();
		jdbcTemplate.update(new PreparedStatementCreator() {
			@Override
			public PreparedStatement createPreparedStatement(Connection connection) throws SQLException {
				PreparedStatement ps = connection.prepareStatement(CommitOrCancelReservation_sql,
						new String[] { "transaction_uuid" });
				ps.setObject(1, resource.getRequestUuid());
				ps.setObject(2, resource.getAccountNumber());
				ps.setObject(3, resource.getDebitCardNumber());
				ps.setObject(4, resource.getTransactionAmount());
				ps.setObject(5, resource.getTransactionTypeCode());
				ps.setObject(6, resource.getRunningBalanceAmount());
				ps.setObject(7, resource.getReservationUuid());
				ps.setObject(8, resource.getTransactionMetaDataJson());
				return ps;
			}
		}, keyHolder);

		try {
			resource.setTransactionUuid( (UUID) keyHolder.getKeys().get("transaction_uuid") );
			log.debug("insertCommitOrCancel UUID {}", resource.getTransactionUuid().toString());
		}
		catch (Exception e) {
			log.error("insertCommitOrCancel, transaction_uuid not returned from insert statement. %s",
					insert_transaction_sql);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
					String.format("transaction_uuid not returned."));
		}
	}

	/**
	 * upsertBalance Inserts or updates the account_balance table for the provided
	 * account_id with the provided running balance.
	 * 
	 * @param account_id        Identifies the account row to update/insert
	 * @param runningBalance_am the new balance to apply
	 */
	public final static String upsert_balance_sql = "UPSERT INTO account_balance (account_id, runningBalance_am) values (?,?);";
	@ExternalResourceSLI(value="jdbc::upsertBalance", ait = "88888", remoteFailures= {DataAccessException.class})
	@Transactional
	public void upsertBalance(final String account_id, final long runningBalance_am) {
		log.trace("upsertBalance ENTRY");

		// Update Balance
		int rowsUpdated = jdbcTemplate.update(upsert_balance_sql, account_id, runningBalance_am);
		if (rowsUpdated == 1) {
			log.debug("upsertBalance New Balance: {}, {}", account_id, runningBalance_am);
		} else {
			log.error("upsertBalance, ERROR=%d rows updated, SQL=%s", rowsUpdated, upsert_balance_sql);
			throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
					String.format("%d (!= 1) account_balance rows updated.", rowsUpdated));
		}
		log.trace("upsertBalance EXIT");
	}

	/**
	 * verifyReservationOpen Verify that no one has already cancelled or committed
	 * the reservation.
	 * 
	 * @param reservation_id The reservation to check
	 */
	public final static String findReservationFinal_sql = "SELECT COUNT(*) from transaction where transactiontype_cd in ('RC','RX') and reservation_uuid=?;";
	@ExternalResourceSLI(value="jdbc::verifyReservationOpen", ait = "88888", remoteFailures= {DataAccessException.class})
	public void verifyReservationOpen(UUID reservation_id) {
		log.trace("verifyReservationOpen ENTRY");

		long rescount = jdbcTemplate.queryForObject(findReservationFinal_sql, new Object[] { reservation_id },
				Long.class);
		if (rescount > 0) {
			log.error(
					"verifyReservationOpen, reservation_uuid (%s) has already been finalized. "
							+ "Another Commit or Cancel transaction with the same reservation_uuid is present.",
					reservation_id);
			throw new ResponseStatusException(HttpStatus.CONFLICT,
					String.format("reservation_uuid (%s) has already been finalized.", reservation_id));
		}
		log.trace("verifyReservationOpen EXIT - Verified Still Open");
	}

	/**
	 * findReservation Find the previous reservation and lock the row
	 * 
	 * @param reservation_id
	 * @return
	 */
	public final static String findReservation_sql = "SELECT account_id, debitCard_id, transaction_am "
			+ "from transaction where transactiontype_cd='RS' and transaction_uuid=? FOR UPDATE;";
	@ExternalResourceSLI(value="jdbc::findReservation", ait = "88888", remoteFailures= {DataAccessException.class})
	public TransactionResource findReservation(UUID reservation_id) {

		log.trace("findReservation ENTRY");
		List<TransactionResource> reservations = jdbcTemplate.query(findReservation_sql,
				new RowMapper<TransactionResource>() {
					public TransactionResource mapRow(ResultSet rs, int rowNum) throws SQLException {
						TransactionResource res = new TransactionResource();
						res.setAccountNumber(rs.getString(1));
						res.setDebitCardNumber(rs.getString(2));
						res.setTransactionAmount(rs.getLong(3));
						return res;
					}
				}, reservation_id);
		if (reservations.size() != 1) {
			log.error("findReservation, reservation_uuid ({}) not found.", reservation_id);
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					String.format("reservation_uuid (%s) not found.", reservation_id));
		}
		log.trace("findReservation EXIT - Reservation Found");
		return reservations.get(0);

	}

	/**
	 * findRelatedToReservation Get all the transactions from the original Reservation
	 * 
	 * @param reservation_id
	 * @return
	 */
	
	public final static String selectRelatedToReservation_sql = "SELECT b.transaction_uuid, b.request_uuid, b.account_id, b.debitCard_id,"
		+ " b.transaction_am, b.transactiontype_cd, b.runningbalance_am, b.reservation_uuid, b.transactionmetadata_json, b.insert_tsz"
		+ "from transaction a, transaction b.request_uuid=a.request_uuid and a.transaction_uuid=? order by insert_tsz asc;";
	
	@ExternalResourceSLI(value="jdbc::findRelatedToReservation", ait = "88888", remoteFailures= {DataAccessException.class})
	public List<TransactionResource> findRelatedToReservation(UUID reservation_id) {
		log.trace("findRelatedToReservation ENTRY");
		
		List<TransactionResource> reservations = jdbcTemplate.query(selectRelatedToReservation_sql,
				new RowMapper<TransactionResource>() {
					public TransactionResource mapRow(ResultSet rs, int rowNum) throws SQLException {
						TransactionResource res = new TransactionResource();
						res.setTransactionUuid(rs.getObject(1, UUID.class));
						res.setRequestUuid(rs.getObject(2, UUID.class));
						res.setAccountNumber(rs.getString(3));
						res.setDebitCardNumber(rs.getString(4));
						res.setTransactionAmount(rs.getLong(5));
						res.setTransactionTypeCode(rs.getString(6));
						res.setRunningBalanceAmount(rs.getLong(7));
						res.setReservationUuid(rs.getObject(8, UUID.class));
						res.setTransactionMetaDataJson(rs.getString(9));
						res.setInsertTimestamp(rs.getTimestamp(10));
						return res;
					}
				}, reservation_id);
		if (reservations.size() != 1) {
			log.error("findRelatedToReservation, reservation_uuid ({}) not found.", reservation_id);
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					String.format("reservation_uuid (%s) not found.", reservation_id));
		}
		
		log.trace("findRelatedToReservation EXIT - Reservation Found");
		return reservations;
	}

	/**
	 * findTransaction Find the specified Transaction
	 * 
	 * @param transaction_uuid
	 * @return
	 */
	public final static String selectTransaction_sql = "SELECT transaction_uuid, request_uuid, account_id, debitCard_id, transaction_am, "
			+ "transactiontype_cd, runningbalance_am, reservation_uuid, transactionmetadata_json, insert_tsz "
			+ "from transaction where transaction_uuid=?;";

	@ExternalResourceSLI(value="jdbc::findTransaction", ait = "88888", remoteFailures= {DataAccessException.class})
	public TransactionResource findTransaction(UUID transaction_uuid) {

		log.trace("findTransaction ENTRY");
		List<TransactionResource> resources = jdbcTemplate.query(selectTransaction_sql,
				new RowMapper<TransactionResource>() {
					public TransactionResource mapRow(ResultSet rs, int rowNum) throws SQLException {
						TransactionResource res = new TransactionResource();
						res.setTransactionUuid(rs.getObject(1, UUID.class));
						res.setRequestUuid(rs.getObject(2, UUID.class));
						res.setAccountNumber(rs.getString(3));
						res.setDebitCardNumber(rs.getString(4));
						res.setTransactionAmount(rs.getLong(5));
						res.setTransactionTypeCode(rs.getString(6));
						res.setRunningBalanceAmount(rs.getLong(7));
						res.setReservationUuid(rs.getObject(8, UUID.class));
						res.setTransactionMetaDataJson(rs.getString(9));
						res.setInsertTimestamp(rs.getTimestamp(10));
						return res;
					}
				}, transaction_uuid);
		if (resources.size() != 1) {
			log.debug("findTransaction, transaction_uuid (%s) not found.", transaction_uuid);
			throw new ResponseStatusException(HttpStatus.NOT_FOUND,
					String.format("transaction_uuid (%s) not found.", transaction_uuid));
		}
		
		log.trace("findTransaction EXIT");
		return resources.get(0);
	}
	/**
	 * checkIdempotency
	 * 
	 * @param request_uuid
	 * @return
	 */
	public final static String idempotentQuery_sql = "SELECT transaction_uuid, request_uuid, account_id, debitcard_id, "
			+ "transaction_am, transactiontype_cd, runningbalance_am, reservation_uuid, transactionmetadata_json, "
			+ "insert_tsz FROM transaction WHERE request_uuid = ? AND account_id = ? order by insert_tsz asc;";
	@ExternalResourceSLI(value="jdbc::checkIdempotency", ait = "88888", remoteFailures= {DataAccessException.class})
	public TransactionResource checkIdempotency(UUID request_uuid, String accountNumber) {
		log.trace("checkIdempotency ENTRY");

		List<TransactionResource> transactions = jdbcTemplate.query(idempotentQuery_sql,
				new RowMapper<TransactionResource>() {
					public TransactionResource mapRow(ResultSet rs, int rowNum) throws SQLException {
						TransactionResource res = new TransactionResource();
						res.setTransactionUuid(rs.getObject(1, UUID.class));
						res.setRequestUuid(rs.getObject(2, UUID.class));
						res.setAccountNumber(rs.getString(3));
						res.setDebitCardNumber(rs.getString(4));
						res.setTransactionAmount(rs.getLong(5));
						res.setTransactionTypeCode(rs.getString(6));
						res.setRunningBalanceAmount(rs.getLong(7));
						res.setReservationUuid(rs.getObject(8, UUID.class));
						res.setTransactionMetaDataJson(rs.getString(9));
						res.setInsertTimestamp(rs.getTimestamp(10));
						return res;
					}
				}, request_uuid, accountNumber);

		log.debug("checkIdempotency EXIT - previous request count = {}", transactions.size());
		return transactions.size() > 0 ? transactions.get(0) : null;
	}

	@ExternalResourceSLI(value="jdbc::checkMultiIdempotency", ait = "88888", remoteFailures= {DataAccessException.class})
	public List<TransactionResource> checkMultiIdempotency(UUID request_uuid, String accountNumber) {
		log.trace("checkIdempotency ENTRY");

		List<TransactionResource> transactions = jdbcTemplate.query(idempotentQuery_sql,
				new RowMapper<TransactionResource>() {
					public TransactionResource mapRow(ResultSet rs, int rowNum) throws SQLException {
						TransactionResource res = new TransactionResource();
						res.setTransactionUuid(rs.getObject(1, UUID.class));
						res.setRequestUuid(rs.getObject(2, UUID.class));
						res.setAccountNumber(rs.getString(3));
						res.setDebitCardNumber(rs.getString(4));
						res.setTransactionAmount(rs.getLong(5));
						res.setTransactionTypeCode(rs.getString(6));
						res.setRunningBalanceAmount(rs.getLong(7));
						res.setReservationUuid(rs.getObject(8, UUID.class));
						res.setTransactionMetaDataJson(rs.getString(9));
						res.setInsertTimestamp(rs.getTimestamp(10));
						return res;
					}
				}, request_uuid, accountNumber);

		log.debug("checkIdempotency EXIT - previous request count = {}", transactions.size());
		return transactions;
	}
}