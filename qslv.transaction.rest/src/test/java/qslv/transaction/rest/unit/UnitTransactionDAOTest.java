package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementCreator;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.web.server.ResponseStatusException;

import qslv.transaction.resource.TransactionResource;
import qslv.transaction.rest.TransactionDAO;

@ExtendWith(MockitoExtension.class)
public class UnitTransactionDAOTest {
	@Mock
	JdbcTemplate jdbcTemplate; 
	TransactionDAO dao = new TransactionDAO();
	
	@BeforeEach
	public void setup() {
		dao.setJdbcTemplate(jdbcTemplate);		
	}
	//--------------------------
	// selectBalanceForUpdate
	//--------------------------
	@Test
	public void testSelectBalanceForUpdate() {		
		Long result = 1001L;
		
		when(jdbcTemplate.queryForObject( eq(TransactionDAO.getBalance_sql) ,eq(Long.class), any())).thenReturn(result);
		assertEquals(dao.selectBalanceForUpdate("12345678012"),result) ;
		verify(jdbcTemplate).queryForObject(any(),  eq(Long.class), any());
	}
	@Test
	public void testSelectBalanceForUpdate_throws() {
		EmptyResultDataAccessException ex = new EmptyResultDataAccessException(1);
		
		when(jdbcTemplate.queryForObject( eq(TransactionDAO.getBalance_sql) ,eq(Long.class), any())).thenThrow(ex);
		assertEquals(dao.selectBalanceForUpdate("12345678012"),0L) ;
		verify(jdbcTemplate).queryForObject(any(),  eq(Long.class), any());
	}

	//--------------------------
	// insertTransaction
	//--------------------------
	@Test
	public void testInsertTransaction() {
		TransactionResource resource = new TransactionResource();
		UUID test_uuid = UUID.randomUUID();
		
		doAnswer(invocation -> {
			KeyHolder keyHolder = invocation.getArgument(1);
			keyHolder.getKeyList().add(Collections.singletonMap("transaction_uuid", test_uuid));
			return null;
		}).when(jdbcTemplate).update( any(PreparedStatementCreator.class), any(KeyHolder.class) );
		dao.insertTransaction(resource);
		assertEquals(resource.getTransactionUuid(), test_uuid) ;
	}
	@Test
	public void testInsertTransaction_throws() {
		TransactionResource resource = new TransactionResource();
		
		doAnswer(invocation -> {
			KeyHolder keyHolder = invocation.getArgument(1);
			keyHolder.getKeyList().add(Collections.singletonMap("transaction_uuid", "String"));
			return null;
		}).when(jdbcTemplate).update( any(PreparedStatementCreator.class), any(KeyHolder.class) );
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { dao.insertTransaction(resource); } );
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	@Test
	public void testInsertTransaction_throws2() {
		TransactionResource resource = new TransactionResource();
		
		doAnswer(invocation -> {
			return null;
		}).when(jdbcTemplate).update( any(PreparedStatementCreator.class), any(KeyHolder.class) );
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { dao.insertTransaction(resource); } );
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	
	//--------------------------
	// insertReservation
	//--------------------------
	@Test
	public void testInsertReservation() {
		TransactionResource resource = new TransactionResource();
		UUID test_uuid = UUID.randomUUID();
		
		doAnswer(invocation -> {
			KeyHolder keyHolder = invocation.getArgument(1);
			keyHolder.getKeyList().add(Collections.singletonMap("transaction_uuid", test_uuid));
			return null;
		}).when(jdbcTemplate).update( any(PreparedStatementCreator.class), any(KeyHolder.class) );
		dao.insertReservation(resource);
		assertEquals(resource.getTransactionUuid(), test_uuid) ;
	}
	@Test
	public void testInsertReservation_throws() {
		TransactionResource resource = new TransactionResource();
		
		doAnswer(invocation -> {
			KeyHolder keyHolder = invocation.getArgument(1);
			keyHolder.getKeyList().add(Collections.singletonMap("transaction_uuid", "String"));
			return null;
		}).when(jdbcTemplate).update( any(PreparedStatementCreator.class), any(KeyHolder.class) );
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { dao.insertReservation(resource); } );
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	@Test
	public void testInsertReservation_throws2() {
		TransactionResource resource = new TransactionResource();
		
		doAnswer(invocation -> {
			return null;
		}).when(jdbcTemplate).update( any(PreparedStatementCreator.class), any(KeyHolder.class) );
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { dao.insertReservation(resource); } );
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	
	//--------------------------
	// upsertBalance
	//--------------------------
	@Test
	public void testUpsertBalance() {		
		when(jdbcTemplate.update( any(String.class), any(String.class), any(Long.class) )).thenReturn(1).thenReturn(0);
		dao.upsertBalance("Account",1L);
		verify(jdbcTemplate).update( any(String.class), any(String.class), any(Long.class) );
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{dao.upsertBalance("Account", 1L);});
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	
	//--------------------------
	// verifyReservationOpen
	//--------------------------
	@Test
	public void testVerifyReservationOpen() {		
		when(jdbcTemplate.queryForObject( any(String.class), any(Object[].class), eq(Long.class) ) ).thenReturn(0L).thenReturn(1L);
		dao.verifyReservationOpen(UUID.randomUUID());
		verify(jdbcTemplate).queryForObject( any(String.class), any(Object[].class), eq(Long.class) );
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{dao.verifyReservationOpen(UUID.randomUUID());});
		assert(ex.getStatus() == HttpStatus.CONFLICT);
	}
	
	//--------------------------
	// findReservation
	//--------------------------
	@Test
	public void testFindReservation() {	
		TransactionResource resource = new TransactionResource();
		resource.setTransactionUuid(UUID.randomUUID());
		
		when(jdbcTemplate.query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class) ) )
			.thenReturn(Collections.singletonList(resource))
			.thenReturn(new ArrayList<TransactionResource>());
		
		TransactionResource result = dao.findReservation(UUID.randomUUID());
		verify(jdbcTemplate).query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class) );
		assert(result.getTransactionUuid().equals(resource.getTransactionUuid()));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{dao.findReservation(UUID.randomUUID());});
		assert(ex.getStatus() == HttpStatus.NOT_FOUND);
	}
	
	//--------------------------
	// findTransaction
	//--------------------------
	@Test
	public void testFindTransaction() {	
		TransactionResource resource = new TransactionResource();
		resource.setTransactionUuid(UUID.randomUUID());
		
		when(jdbcTemplate.query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class) ) )
			.thenReturn(Collections.singletonList(resource))
			.thenReturn(new ArrayList<TransactionResource>());
		
		TransactionResource result = dao.findTransaction(UUID.randomUUID());
		verify(jdbcTemplate).query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class) );
		assert(result.getTransactionUuid().equals(resource.getTransactionUuid()));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{dao.findTransaction(UUID.randomUUID());});
		assert(ex.getStatus() == HttpStatus.NOT_FOUND);
	}
	
	//--------------------------
	// checkIdempotency
	//--------------------------
	@Test
	public void testCheckIdempotency() {	
		TransactionResource resource = new TransactionResource();
		resource.setTransactionUuid(UUID.randomUUID());
		
		when(jdbcTemplate.query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class) ) )
			.thenReturn(Collections.singletonList(resource))
			.thenReturn(new ArrayList<TransactionResource>());
		
		TransactionResource result = dao.checkIdempotency(UUID.randomUUID());
		verify(jdbcTemplate).query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class) );
		assert(result.getTransactionUuid().equals(resource.getTransactionUuid()));

		result = dao.checkIdempotency(UUID.randomUUID());
		verify(jdbcTemplate, times(2)).query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class) );
		assert(result == null);
	}
}
