def factorial(x):
	res = 1
	for i in range(1, x+1):
		res *= i
	print("The factorial of ", x, " is ", res)


def sum(x):
	res = 0
	for i in range(x+1):
		res += i
	print("The sum from 1 to ", x, " is ", res)
	
k = 5
factorial(k)
sum(k)