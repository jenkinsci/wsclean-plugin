# Change Log

### Version 1.0.7
_September 24th, 2019:_
* Functionality unchanged from 1.0.6.
* :+1:
Meta-data for plugin now lists license and developers (past and present).

### Version 1.0.6
_August 28th, 2019:_
* :x:
Don't clean concurrently-running builds
([JENKINS-43269](https://issues.jenkins-ci.org/browse/JENKINS-43269))
* :+1:
Implement timeouts so dud slave nodes don't block all builds
* :+1:
Implement "parallel cleanup" to improve performance
* :+1:
Implement "skip node" node property and skip-by-name functionality
* :+1:
Make "Skip roaming" configurable

### Version 1.0.5
_August 6th, 2015:_
* :x:
Fix Compatibility with Folder plugin
([JENKINS-29682](https://issues.jenkins-ci.org/browse/JENKINS-29682))
* :x:
Fix deletion on master
* :x:
Don't wait for slave to become online/don't try to reconnect slave, if slave is offline
* :+1:
Various cleanup/refactoring
* :+1:
First release from GitHub

### Version 1.0.4
_January 7th, 2010:_
* :x:
Fix
[NPE](http://n4.nabble.com/Hudson-bug-with-Ehcache-td787618.html)
while running PrePostClean on a project that can roam
* :+1:
Update code for more recent Hudson

### Version 1.0.3
_October 12th, 2009:_
* :x:
Fix broken classinformation due to change of from interface to abstract class.
* :x:
Fix
[JENKINS-4630](https://issues.jenkins-ci.org/browse/JENKINS-4630)
* :x:
NPE while running PrePostClean without any slaves

### Version 1.0.2
* :x:
Fix for release

### Version 1.0.1
* :+1:
Initial checkin
