output "vpc_id" {
  description = "ID of the VPC."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "IDs of the public subnets, one per Availability Zone, in AZ order."
  value       = aws_subnet.public[*].id
}

output "private_subnet_ids" {
  description = "IDs of the private subnets, one per Availability Zone, in AZ order."
  value       = aws_subnet.private[*].id
}

output "public_subnet_availability_zones" {
  description = "Availability Zone of each public subnet, in the same order as public_subnet_ids."
  value       = aws_subnet.public[*].availability_zone
}

output "private_subnet_availability_zones" {
  description = "Availability Zone of each private subnet, in the same order as private_subnet_ids."
  value       = aws_subnet.private[*].availability_zone
}

output "nat_gateway_ids" {
  description = "IDs of the NAT Gateways. Empty when enable_nat_gateway is false, one element when it is true."
  value       = aws_nat_gateway.this[*].id
}

output "nat_eip_count" {
  description = "Number of Elastic IPs created for NAT. 0 when enable_nat_gateway is false, 1 when it is true."
  value       = length(aws_eip.nat)
}
