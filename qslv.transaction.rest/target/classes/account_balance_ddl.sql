create table account_balance(
	account_id STRING PRIMARY KEY not null,
	runningBalance_am INT8 not null default 0
);
