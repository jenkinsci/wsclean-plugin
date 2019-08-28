# wsclean-plugin
[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/wsclean-plugin/master)](https://ci.jenkins.io/job/Plugins/job/wsclean-plugin/job/master/)

The Distributed Workspace Clean plugin is used to remove unnecessary old build workspaces from slave nodes used by previous builds.
This allows us to keep the overall disk usage down on long-lifetime slave nodes that do lots of different builds.

e.g. if you have 100 jobs that typically uses 1gig of disk to build,
and you have 100 slave nodes that can build those jobs,
then _without this plugin_ you'll eventually end up with a copy of every workspace on every node,
10000gigs total.
However, with this plugin active on every build,
you'd only keep one workspace for each job across all the nodes,
100gigs total, which is a lot less data to back up etc.

If you have a lot of static slave nodes then you may find this useful.
If you only have dynamic cloud-provided disposable nodes then you probably won't.
