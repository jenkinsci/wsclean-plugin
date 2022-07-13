# Change Log

### Version 1.0.7
_September 24th, 2019:_
* :grey_exclamation: Functionality unchanged from 1.0.6.
* :heavy_check_mark:
Meta-data for plugin now lists license and developers (past and present).

### Version 1.0.6
_August 28th, 2019:_
* :x:
Don't clean concurrently-running builds
([JENKINS-43269](https://issues.jenkins-ci.org/browse/JENKINS-43269))
* :heavy_check_mark:
Implement timeouts so dud agent nodes don't block all builds
* :heavy_check_mark:
Implement "parallel cleanup" to improve performance
* :heavy_check_mark:
Implement "skip node" node property and skip-by-name functionality
* :heavy_check_mark:
Make "Skip roaming" configurable

### Version 1.0.5
_August 6th, 2015:_
* :x:
Fix Compatibility with Folder plugin
([JENKINS-29682](https://issues.jenkins-ci.org/browse/JENKINS-29682))
* :x:
Fix deletion on controller
* :x:
Don't wait for agent to become online/don't try to reconnect agent, if agent is offline
* :heavy_check_mark:
Various cleanup/refactoring
* :heavy_check_mark:
First release from GitHub

### Version 1.0.4
_January 7th, 2010:_
* :x:
Fix
[NPE](http://n4.nabble.com/Hudson-bug-with-Ehcache-td787618.html)
while running PrePostClean on a project that can roam
* :heavy_check_mark:
Update code for more recent Hudson

### Version 1.0.3
_October 12th, 2009:_
* :x:
Fix broken classinformation due to change of from interface to abstract class.
* :x:
Fix
[JENKINS-4630](https://issues.jenkins-ci.org/browse/JENKINS-4630)
* :x:
NPE while running PrePostClean without any agents

### Version 1.0.2
* :x:
Fix for release

### Version 1.0.1
* :heavy_check_mark:
Initial checkin
