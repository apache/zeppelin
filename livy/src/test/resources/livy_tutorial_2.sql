select age, count(1) value
from bank
where age < 30
group by age
order by age