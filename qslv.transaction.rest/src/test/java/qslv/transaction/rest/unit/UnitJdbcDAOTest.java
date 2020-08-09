package qslv.transaction.rest.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import qslv.transaction.rest.JdbcDao;

@ExtendWith(MockitoExtension.class)
public class UnitJdbcDAOTest {
	@Mock
	JdbcTemplate jdbcTemplate; 
	JdbcDao jdbcDao = new JdbcDao();
	
	@BeforeEach
	public void setup() {
		jdbcDao.setJdbcTemplate(jdbcTemplate);		
	}
	//--------------------------
	// selectBalanceForUpdate
	//--------------------------
	@Test
	public void testSelectBalanceForUpdate() {		
		Long result = 1001L;
		
		when(jdbcTemplate.queryForObject( eq(JdbcDao.getBalance_sql) ,eq(Long.class), any())).thenReturn(result);
		assertEquals(jdbcDao.selectBalanceForUpdate("12345678012"),result) ;
		verify(jdbcTemplate).queryForObject(any(),  eq(Long.class), any());
	}
	@Test
	public void testSelectBalanceForUpdate_throws() {
		EmptyResultDataAccessException ex = new EmptyResultDataAccessException(1);
		
		when(jdbcTemplate.queryForObject( eq(JdbcDao.getBalance_sql) ,eq(Long.class), any())).thenThrow(ex);
		assertEquals(jdbcDao.selectBalanceForUpdate("12345678012"),0L) ;
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
		jdbcDao.insertTransaction(resource);
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
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { jdbcDao.insertTransaction(resource); } );
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	@Test
	public void testInsertTransaction_throws2() {
		TransactionResource resource = new TransactionResource();
		
		doAnswer(invocation -> {
			return null;
		}).when(jdbcTemplate).update( any(PreparedStatementCreator.class), any(KeyHolder.class) );
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { jdbcDao.insertTransaction(resource); } );
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
		jdbcDao.insertCommitOrCancel(resource);
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
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { jdbcDao.insertCommitOrCancel(resource); } );
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	@Test
	public void testInsertReservation_throws2() {
		TransactionResource resource = new TransactionResource();
		
		doAnswer(invocation -> {
			return null;
		}).when(jdbcTemplate).update( any(PreparedStatementCreator.class), any(KeyHolder.class) );
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()-> { jdbcDao.insertCommitOrCancel(resource); } );
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	
	//--------------------------
	// upsertBalance
	//--------------------------
	@Test
	public void testUpsertBalance() {		
		when(jdbcTemplate.update( any(String.class), any(String.class), any(Long.class) )).thenReturn(1).thenReturn(0);
		jdbcDao.upsertBalance("Account",1L);
		verify(jdbcTemplate).update( any(String.class), any(String.class), any(Long.class) );
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{jdbcDao.upsertBalance("Account", 1L);});
		assert(ex.getStatus() == HttpStatus.INTERNAL_SERVER_ERROR);
	}
	
	//--------------------------
	// verifyReservationOpen
	//--------------------------
	@Test
	public void testVerifyReservationOpen() {		
		when(jdbcTemplate.queryForObject( any(String.class), any(Object[].class), eq(Long.class) ) ).thenReturn(0L).thenReturn(1L);
		jdbcDao.verifyReservationOpen(UUID.randomUUID());
		verify(jdbcTemplate).queryForObject( any(String.class), any(Object[].class), eq(Long.class) );
		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{jdbcDao.verifyReservationOpen(UUID.randomUUID());});
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
		
		TransactionResource result = jdbcDao.findReservation(UUID.randomUUID());
		verify(jdbcTemplate).query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class) );
		assert(result.getTransactionUuid().equals(resource.getTransactionUuid()));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{jdbcDao.findReservation(UUID.randomUUID());});
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
		
		TransactionResource result = jdbcDao.findTransaction(UUID.randomUUID());
		verify(jdbcTemplate).query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class) );
		assert(result.getTransactionUuid().equals(resource.getTransactionUuid()));

		ResponseStatusException ex = assertThrows(ResponseStatusException.class, ()->{jdbcDao.findTransaction(UUID.randomUUID());});
		assert(ex.getStatus() == HttpStatus.NOT_FOUND);
	}
	
	//--------------------------
	// checkIdempotency
	//--------------------------
	@Test
	public void testCheckIdempotency() {	
		TransactionResource resource = new TransactionResource();
		resource.setTransactionUuid(UUID.randomUUID());
		
		when(jdbcTemplate.query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class), anyString() ) )
			.thenReturn(Collections.singletonList(resource))
			.thenReturn(new ArrayList<TransactionResource>());
		
		TransactionResource result = jdbcDao.checkIdempotency(UUID.randomUUID(), "TEST_ACCOUNT");
		verify(jdbcTemplate).query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class), anyString() );
		assertNotNull(result);
		assertEquals(resource.getTransactionUuid(), result.getTransactionUuid());

		result = jdbcDao.checkIdempotency(UUID.randomUUID(), "TEST_ACCOUNT");
		verify(jdbcTemplate, times(2)).query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class), anyString() );
		assertNull( result);
	}
	//--------------------------
	// checkIdempotency
	//--------------------------
	@Test
	public void checkMultiIdempotency() {	
		TransactionResource resource = new TransactionResource();
		resource.setTransactionUuid(UUID.randomUUID());
		
		when(jdbcTemplate.query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class), anyString() ) )
			.thenReturn(Collections.singletonList(resource));
		
		List<TransactionResource> result = jdbcDao.checkMultiIdempotency(UUID.randomUUID(), "TEST_ACCOUNT");
		verify(jdbcTemplate).query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class), anyString() );
		assertNotNull(result);
		assertSame(resource, result.get(0));

		verify(jdbcTemplate, times(1)).query( any(String.class), ArgumentMatchers.<RowMapper<TransactionResource>>any(), any(UUID.class), anyString() );
	}
}
