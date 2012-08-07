This module provides the auxiliary data for the BEAM Watermask Operator module.
If this module is checked out from VCS for the first time it is empty.
Normally this auxdata-artifact is already deployed to the BC maven repository.
So no action has to be undertaken.
See also the readme.txt in the beam-watermask-operator module.
The current auxdata files are located on the internal BC server in 'projects/ongoing/BEAM/beam-maintenance/software/watermask'.

If new auxdata shall be deployed follow the steps given below:

* Increase the version number of the beam-watermask-auxdata module
* Place the new auxiliary data into the 'src/main/resources/auxdata/images' directory
* Update also the auxdata on the internal BC server
* Run 'mvn deploy' from the module directory 'beam-watermask-auxdata'
* Update the dependency version to the beam-watermask-auxdata module in the
  maven-remote-resources-plugin configuration of the beam-watermask-operator module
