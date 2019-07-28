---
title: "Testing concurrency in rails"
date: 2012-07-02T08:00:00+02:00
draft: true
aliases:
  - /testing-concurrency-in-rails
---

Concurrency is hard to get right, and unfortunately it is hard to test as well.
In this article, we show how to use the ruby gem
[fork_break](http://github.com/remen/fork_break) to manually set breakpoints
within tests which allows us to verify concurrent behaviour.

<!--more-->

## A race condition

Let's start with a simple controller action.

```ruby
# app/controllers/counters_controller.rb
class CountersController < ApplicationController
  def increment
    counter = Counter.find(params[:id])
    counter.increment!(:value)
    render text: counter.value
  end
end
```

Looks good, right? Well, there is a race condition between the find and the
increment!. Let’s see if we can add a failing test for this before fixing it.
For this, we’ll use the gem [fork_break](http://github.com/remen/fork_break)
which allows you to start subprocesses in which to execute your code, while
synchronizing them from the parent process using breakpoints.

## Adding a failing test case

Now, for the following to work, you have to make sure that your tests are not
being run in transactions.

```ruby
# spec/spec_helper.rb
Rspec.config do |config|
  ...
  # Make sure this is not set to true
  config.use_transactional_fixtures = false
 
  ...
end
```

With this setting, and a dependency on
[fork_break](http://github.com/remen/fork_break), we can write the following
test case:

```ruby
# spec/controllers/counters_controller_spec.rb
require 'spec_helper'
 
describe CountersController do
  it "works for concurrent increments" do
    counter = Counter.create!
     
    # For postgresql we need to disconnect before forking
    # and for other databases, it won't hurt.
    ActiveRecord::Base.connection.disconnect!
 
    process1, process2 = 2.times.map do
      ForkBreak::Process.new do |breakpoints|
        # We need to reconnect after forking
        ActiveRecord::Base.establish_connection
 
        # Add a breakpoint after invoking find       
        original_find = Counter.method(:find)
        Counter.stub!(:find) do |*args|
          counter = original_find.call(*args)
          breakpoints << :after_find
          counter
        end
 
        get :increment, :id => counter.id
      end
    end
    process1.run_until(:after_find).wait
    process2.run_until(:after_find).wait
 
    process1.finish.wait
    process2.finish.wait
     
    # The parent process also needs a new connection
    ActiveRecord::Base.establish_connection
    counter.reload.value.should == 2
  end
end
```

Running it yields

```
$ rspec spec/controllers/counters_controller_spec.rb

CountersController
  works for concurrent increments (FAILED - 1)
```

## Fixing the issue

Excellent, a failing test case! Ok, so how do we fix this? For this example,
we’ll use [pessimistic locking](http://api.rubyonrails.org/classes/ActiveRecord/Locking/Pessimistic.html).

```ruby
# app/controllers/counters_controller.rb
class CountersController < ApplicationController
  def increment
    Counter.transaction do
      counter = Counter.find(params[:id], lock: true)
      counter.increment!(:value)
      render text: counter.value
    end
  end
end
```

However, if we run the spec it just hangs (eventually raising an exception after
the database has timed out). The reason for this is that `Counter.find` is
blocking in `process2`. To fix this, we’ll add have to modify the test somewhat.

```ruby
# spec/controllers/counters_controller_spec.rb
require 'spec_helper'
 
describe CountersController do
  it "works for concurrent increments" do
    counter = Counter.create!
 
    # For postgresql we need to disconnect before forking
    # and for other databases, it won't hurt.
    ActiveRecord::Base.connection.disconnect!
 
    process1, process2 = 2.times.map do
      ForkBreak::Process.new do
        # We need to reconnect after forking
        ActiveRecord::Base.establish_connection
 
        # Add a breakpoint after invoking find       
        original_find = Counter.method(:find)
        Counter.stub!(:find) do |*args|
          breakpoints << :before_find
          counter = original_find.call(*args)
          breakpoints << :after_find
          counter
        end
 
        get :increment, :id => counter.id
      end
    end
    process1.run_until(:after_find).wait
 
    # Try to make sure that process2 is blocking in find
    process2.run_until(:before_find).wait
    process2.run_until(:after_find) && sleep(0.1)
 
    # Now finish process1 and wait for process2
    process1.finish.wait
    process2.finish.wait
     
    # The parent process also needs a new connection
    ActiveRecord::Base.establish_connection
    counter.reload.value.should == 2
  end
end
```

```
$ rspec spec/controllers/counters_controller_spec.rb

CountersController
  works for concurrent increments
```

*Huzza!!* (of course, seeing how we changed the test, we need make sure that the
original code fails)

Thanks to
http://blog.ardes.com/2006/12/12/testing-concurrency-in-rails-using-fork for
getting me started on this.

## Updates
---

[2019-07-27] Time flies. The original post was deleted along with the comments,
due to me forgetting to update the credit card with the web host provider.
Resurrected the post using the wayback machine. NB: I no longer use ruby on
rails professionally so I don't know how much of this still applies.

[2013-05-15] Updated code example to work with postgresql. Many thanks to Carlos
Alonso in the comments for finding both the problem and the solution!

---
