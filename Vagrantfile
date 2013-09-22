# -*- mode: ruby -*-
# vi: set ft=ruby :

Vagrant.configure("2") do |config|
  # All Vagrant configuration is done here. The most common configuration
  # options are documented and commented below. For a complete reference,
  # please see the online documentation at vagrantup.com.

  # Every Vagrant virtual environment requires a box to build off of.
  config.vm.box = "TesseraeNG-32-v5"

  # The url from where the 'config.vm.box' box will be fetched if it
  # doesn't already exist on the user's system.
  config.vm.box_url = "http://share-static.chriseberle.net/wheezy32-tesserae-ng-v5.box"

  # Create a forwarded port mapping which allows access to a specific port
  # within the machine from a port on the host machine. In the example below,
  # accessing "localhost:8080" will access port 80 on the guest machine.
  config.vm.network :forwarded_port, guest: 80, host: 8000, auto_correct: true
  config.vm.network :forwarded_port, guest: 8080, host: 8080, auto_correct: true
  config.vm.network :forwarded_port, guest: 9000, host: 9000, auto_correct: true
  config.vm.network :forwarded_port, guest: 9099, host: 9099, auto_correct: true
  config.vm.network :forwarded_port, guest: 15672, host: 15672, auto_correct: true

  # The default image is best suited for the least-common denominator (which is
  # to say, i386 with one CPU and 1GB of memory). If you want the image to use
  # more resources, uncomment the following and adjust to your needs.
  # Pro-tip, throw these settings into ~/.vagrant.d/Vagrantfile to avoid
  # modifying this file.
  #config.vm.provider :virtualbox do |vb|
  #  vb.customize ["modifyvm", :id, "--memory", "2048"]
  #  vb.customize ["modifyvm", :id, "--cpus", "4"]
  #  vb.customize ["modifyvm", :id, "--ioapic", "on"]
  #  vb.customize ["modifyvm", :id, "--pae", "on"]
  #end

  # Run the following provisioner script once fully booted
  config.vm.provision :shell, :path => "scripts/bootstrap.sh"
end
