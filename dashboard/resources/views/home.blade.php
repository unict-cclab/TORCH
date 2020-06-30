@extends('layouts.app')

<?php $user = Auth::user()?>

@section('content')
<div class="container">
    @if( count($user->templates) > 0 )
    <div class="row justify-content-center">
        <div class="col-md-12">
            <div class="card">
                <table class="table mb-0">
                    <thead class="card-header">
                        <th scope="col">TOSCA Scheme</th>
                        <th scope="col">Status</th>
                        <th scope="col">Last action</th>
                        <th scope="col">Last edit</th>
                        <th class="text-center" scope="col">Delete</th>
                    </thead>
                    <tbody class="card-body">
                        @foreach ($user->templates as $template)
                            <tr>
                                <th scoper="row"><a href="{{ route('user.template.show', ['user' => $user, 'template' => $template])}}">{{$template->name}}</a></th>
                                <td>
                                    @if($template->active)
                                        Active
                                    @else
                                        Not Deployed                                        
                                    @endif
                                </td>
                                <td>{{ $template->created_at }}</td>
                                <td>{{ $template->updated_at }}</td>
                                <td class="text-center font-weight-bold">
                                    <form method="POST" action="{{route('user.template.destroy', ['user' => $user, 'template' => $template])}}">
                                        @method('DELETE')
                                        @csrf
                                        <button type="submit" class="btn btn-link text-danger font-weight-bold">X</button>
                                    </form>
                                </td>
                            </tr>
                        @endforeach
                    </tbody>
                </table>
            </div>
        </div>
    </div>
    @endif
    
    <div class="row justify-content-center mt-3">
        @if( count($user->templates) > 0 )
        <div class="col-md-6">
            <card-widget title="Last deployment">
                <div>
                    <span class="float-left">
                        Status:
                        @if($template->active)
                            Active
                        @else
                            Not Deployed                                        
                        @endif
                    </span>    
                    <a href="{{ route('user.template.show', ['user' => $user, 'template' => $user->templates[0]])}}" class="float-right">
                        {{ $user->templates->last()->name}}
                    </a>    
                    <br>
                </div>
                <div class="border" style="height: 300px">
                    <tosca-graph json_graph="{{$user->templates->last()->json_graph}}"></tosca-graph>
                </div>
            </card-widget>
        </div>
        @endif
        <div class="col-md-6">
            <card-widget title="Review the TOSCA standard">
                <div>
                    Our framework heavily relies on TOSCA Simple Profile.
                    <br />
                    <div class="text-center my-3">
                        <img src="/img/oasis_logo.png" class="img-fluid" alt="TOSCA logo"> 
                    </div>
                    Review YAML syntax:
                    <a href="http://docs.oasis-open.org/tosca/TOSCA-Simple-Profile-YAML/v1.2/csd01/TOSCA-Simple-Profile-YAML-v1.2-csd01.html">
                        TOSCA Simple Profile
                    </a>
                </div>
            </card-widget>
        </div>    
    </div>
</div>
@endsection
