INSERT INTO configuration (config_key, config_value)
SELECT 'WEIGHT_GOAL_KG', '75.0'
WHERE NOT EXISTS (SELECT 1 FROM configuration WHERE config_key = 'WEIGHT_GOAL_KG');

INSERT INTO configuration (config_key, config_value)
SELECT 'WEIGHT_GOAL_STARTED_ON', CAST(CURRENT_DATE AS CHAR)
WHERE NOT EXISTS (SELECT 1 FROM configuration WHERE config_key = 'WEIGHT_GOAL_STARTED_ON');
